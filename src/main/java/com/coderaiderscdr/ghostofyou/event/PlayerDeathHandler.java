package com.coderaiderscdr.ghostofyou.event;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.coderaiderscdr.ghostofyou.entity.ModEntities;
import com.coderaiderscdr.ghostofyou.entity.SoulCrystalEntity;
import com.coderaiderscdr.ghostofyou.recording.ActionEventLog;
import com.coderaiderscdr.ghostofyou.recording.CircularFrameBuffer;
import com.coderaiderscdr.ghostofyou.recording.PlayerRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Listens for player death events and spawns a {@link GhostEntity} at the
 * death location, seeded with the player's recording buffer.
 *
 * <p>Also enforces per-chunk and per-player ghost caps via FIFO eviction.
 */
public class PlayerDeathHandler {

    private PlayerDeathHandler() {}

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (!ConfigManager.isGhostSpawningEnabled()) return;

        ServerLevel level = player.serverLevel();

        // ---- Determine death position ----
        double deathX = player.getX();
        double deathY = player.getY();
        double deathZ = player.getZ();

        // Handle void death: use last safe Y recorded by PlayerRecorder
        PlayerRecorder recorder = PlayerTickHandler.getRecorder(player.getUUID());
        if (recorder != null && deathY < level.getMinBuildHeight()) {
            double[] safe = recorder.getLastSafePosition();
            deathY = safe[1];
        }

        if (recorder == null) return;

        recorder.captureImmediateFrame();

        CircularFrameBuffer buffer = recorder.getFrameBuffer();
        ActionEventLog eventLog    = recorder.getEventLog();

        if (buffer.size() == 0) {
            GhostOfYou.LOGGER.debug("Skipping ghost spawn for {} — no frames recorded", player.getName().getString());
            return;
        }

        // ---- Build the ghost ----
        GhostEntity ghost = ModEntities.GHOST.get().create(level);
        if (ghost == null) return;

        ghost.initFromRecording(player, deathX, deathY, deathZ,
                buffer, eventLog.getAll());

        // ---- Cap enforcement (before adding to world) ----
        enforceChunkCap(level, deathX, deathZ);
        enforcePlayerCap(level, player.getUUID());

        level.addFreshEntity(ghost);

        // ---- Spawn Soul Crystal at death location ----
        // The crystal is the stationary, always-visible interaction point.
        // It hovers at the death coords, glows with soul-fire particles,
        // and is the target for the Ghost Banisher right-click.
        SoulCrystalEntity crystal = ModEntities.SOUL_CRYSTAL.get().create(level);
        if (crystal != null) {
            crystal.setPos(deathX, deathY, deathZ);
            crystal.setLinkedGhostUUID(ghost.getUUID());
            crystal.setOwnerName(player.getName().getString());
            level.addFreshEntity(crystal);
        }

        GhostOfYou.LOGGER.info("Spawned ghost of {} at ({},{},{}) with {} frames",
                player.getName().getString(), deathX, deathY, deathZ, buffer.size());

        // Notify the player so they know where to find their ghost after respawn
        player.sendSystemMessage(Component.translatable("ghostofyou.ghost_spawned",
                (int) deathX, (int) deathY, (int) deathZ, buffer.size()));

        // Reset the recorder buffer so the next life starts fresh
        recorder.rebuildBuffer();
    }

    // ------------------------------------------------------------------
    // Cap enforcement
    // ------------------------------------------------------------------

    /**
     * If the chunk at (deathX, deathZ) already has {@code maxGhostsPerChunk}
     * ghosts, remove the oldest one (FIFO).
     */
    private static void enforceChunkCap(ServerLevel level, double deathX, double deathZ) {
        int max = ConfigManager.maxGhostsPerChunk();
        BlockPos chunkOrigin = new BlockPos(
                (int) deathX & ~15,
                level.getMinBuildHeight(),
                (int) deathZ & ~15);
        AABB chunkBox = new AABB(chunkOrigin, chunkOrigin.offset(16, level.getHeight(), 16));

        List<GhostEntity> inChunk = level.getEntitiesOfClass(GhostEntity.class, chunkBox);
        if (inChunk.size() >= max) {
            inChunk.stream()
                    .min(Comparator.comparingLong(GhostEntity::getCreationTime))
                    .ifPresent(Entity::discard);
        }
    }

    /**
     * If the player already has {@code maxGhostsPerPlayer} ghosts in this
     * dimension, remove the oldest one (FIFO).
     */
    private static void enforcePlayerCap(ServerLevel level, UUID ownerUUID) {
        int max = ConfigManager.maxGhostsPerPlayer();
        java.util.List<GhostEntity> playerGhosts = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof GhostEntity g && g.isAlive() && ownerUUID.equals(g.getOwnerUUID())) {
                playerGhosts.add(g);
            }
        }
        while (playerGhosts.size() >= max) {
            GhostEntity oldest = playerGhosts.stream()
                    .min(Comparator.comparingLong(GhostEntity::getCreationTime))
                    .orElse(null);
            if (oldest == null) break;
            oldest.discard();
            playerGhosts.remove(oldest);
        }
    }
}
