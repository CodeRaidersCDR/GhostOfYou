package com.coderaiderscdr.ghostofyou.event;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.coderaiderscdr.ghostofyou.entity.ModEntities;
import com.coderaiderscdr.ghostofyou.entity.SoulCrystalEntity;
import com.coderaiderscdr.ghostofyou.recording.ActionEventLog;
import com.coderaiderscdr.ghostofyou.recording.CircularFrameBuffer;
import com.coderaiderscdr.ghostofyou.recording.PlayerRecorder;
import com.coderaiderscdr.ghostofyou.util.ModLogger;
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
 */
public class PlayerDeathHandler {

    private PlayerDeathHandler() {}

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (!ConfigManager.isGhostSpawningEnabled()) return;

        ServerLevel level = player.serverLevel();

        double deathX = player.getX();
        double deathY = player.getY();
        double deathZ = player.getZ();

        PlayerRecorder recorder = PlayerTickHandler.getRecorder(player.getUUID());
        if (recorder == null) {
            ModLogger.SPAWN.warn("No recorder for {} at death — skipping ghost spawn",
                    player.getName().getString());
            return;
        }

        if (deathY < level.getMinBuildHeight()) {
            double[] safe = recorder.getLastSafePosition();
            deathY = safe[1];
        }

        recorder.captureImmediateFrame();

        CircularFrameBuffer buffer = recorder.getFrameBuffer();
        ActionEventLog eventLog = recorder.getEventLog();

        if (buffer.size() == 0) {
            ModLogger.SPAWN.info("Empty buffer for {} — using emergency death frames",
                    player.getName().getString());
            recorder.captureEmergencyDeathFrames(deathX, deathY, deathZ);
        }

        GhostEntity ghost = ModEntities.GHOST.get().create(level);
        if (ghost == null) return;

        ghost.initFromRecording(player, deathX, deathY, deathZ, buffer, eventLog.getAll());

        // Store death cause so Ghost Essence tooltip shows the actual cause (e.g. "fall", "player")
        String deathCauseKey = "death.attack." + event.getSource().getMsgId();
        ghost.setDeathCauseKey(deathCauseKey);

        enforceChunkCap(level, deathX, deathZ);
        enforcePlayerCap(level, player.getUUID());

        level.addFreshEntity(ghost);

        SoulCrystalEntity crystal = ModEntities.SOUL_CRYSTAL.get().create(level);
        if (crystal != null) {
            crystal.setPos(deathX, deathY, deathZ);
            crystal.setLinkedGhostUUID(ghost.getUUID());
            crystal.setOwnerName(player.getName().getString());
            level.addFreshEntity(crystal);
        }

        ModLogger.SPAWN.info("Spawned ghost of {} at ({},{},{}) with {} frames",
                player.getName().getString(), deathX, deathY, deathZ, buffer.size());

        player.sendSystemMessage(Component.translatable("ghostofyou.ghost_spawned",
                (int) deathX, (int) deathY, (int) deathZ, buffer.size()));

        recorder.rebuildBuffer();
    }

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

    private static void enforcePlayerCap(ServerLevel level, UUID ownerUUID) {
        int max = ConfigManager.maxGhostsPerPlayer();
        List<GhostEntity> playerGhosts = new ArrayList<>();
        for (GhostEntity ghost : level.getEntities(ModEntities.GHOST.get(),
                ghost -> ghost.isAlive() && ownerUUID.equals(ghost.getOwnerUUID()))) {
            playerGhosts.add(ghost);
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
