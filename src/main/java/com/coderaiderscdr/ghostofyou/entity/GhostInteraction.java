package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.event.ServerTickHandler;
import com.coderaiderscdr.ghostofyou.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the server-side state of Ghost Banisher channeling.
 *
 * <p>When a player right-clicks a {@link GhostEntity} with a
 * {@link com.coderaiderscdr.ghostofyou.item.GhostBanisherItem} the entity calls
 * {@link #startBanishing}. The item's {@code onUseTick} / {@code finishUsingItem}
 * hooks then query and complete the banish via this class.
 */
public final class GhostInteraction {

    /** player UUID → UUID of the ghost being channeled */
    private static final Map<UUID, UUID> activeBanishings = new HashMap<>();

    private GhostInteraction() {}

    // ------------------------------------------------------------------
    // Start / cancel / complete
    // ------------------------------------------------------------------

    /**
     * Begin a banishing interaction.
     * Calls {@link Player#startUsingItem} so the item enters the "in-use" state
     * and the game tracks the 60-tick duration automatically.
     *
     * @param player the player holding the banisher
     * @param ghost  the ghost being targeted
     * @param hand   the hand holding the banisher
     */
    public static void startBanishing(Player player, GhostEntity ghost, InteractionHand hand) {
        if (activeBanishings.containsKey(player.getUUID())) return; // already channeling
        activeBanishings.put(player.getUUID(), ghost.getUUID());
        player.startUsingItem(hand);

        // Play a random exorcist incantation (Minecraft picks one of the 4 variants)
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.playSound(null, ghost.blockPosition(),
                    ModSounds.GHOST_EXORCIST.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("ghostofyou.banish.start"),
                true);
    }

    /**
     * Cancel a banishing in progress (player moved or released use).
     *
     * @param playerId the player's UUID
     */
    public static void cancelBanishing(UUID playerId) {
        if (activeBanishings.remove(playerId) != null) {
            // Message sent by releaseUsing in the item
        }
    }

    /**
     * Complete a banishing: start death animation, schedule essence drop, play effect.
     *
     * @param player     the player
     * @param stack      the Ghost Banisher item stack
     */
    public static void completeBanishing(Player player, ItemStack stack) {
        UUID ghostId = activeBanishings.remove(player.getUUID());
        if (ghostId == null) return;

        if (!(player.level() instanceof ServerLevel sl)) return;
        net.minecraft.world.entity.Entity entity = sl.getEntity(ghostId);
        if (!(entity instanceof GhostEntity ghost) || !ghost.isAlive()) return;

        // Snapshot all ghost data before the entity is dismissed
        final String ownerName    = ghost.getOwnerName();
        final String deathCause   = ghost.getDeathCauseKey();
        final String killerName   = ghost.getKillerName();
        final long   livedMinutes = Math.max(0,
                (sl.getGameTime() - ghost.getPlayerFirstLoginTime()) / 1200L);
        final long   banishedAt   = sl.getGameTime();
        final double gx = ghost.getX(), gy = ghost.getY(), gz = ghost.getZ();
        // Capture recording before startBanishmentDeath() nulls the playbackController
        final PlaybackController pc__ = ghost.getPlaybackController();
        final net.minecraft.nbt.CompoundTag recordingNbt = (pc__ != null) ? pc__.saveToNbt(gx, gy, gz) : null;

        // Kick off the banishment death animation (stops playback, soul particles each tick)
        ghost.startBanishmentDeath();

        // Phase 1 particles + sounds happen immediately
        playBanishmentPhase1(sl, gx, gy, gz);

        // Phase 2 (end-rod beacon column) follows 10 ticks later
        ServerTickHandler.scheduleDelayed(sl.getServer(), 10,
                () -> playBanishmentPhase2(sl, gx, gy, gz));

        // Essence drop + player reward execute after the full death animation
        final ItemStack essenceStack = buildEssenceStack(
                ownerName, deathCause, killerName, livedMinutes, banishedAt, recordingNbt);
        ServerTickHandler.scheduleDelayed(sl.getServer(),
                GhostEntity.DEATH_ANIMATION_DURATION + 2, () -> {
            sl.addFreshEntity(new ItemEntity(sl, gx, gy, gz, essenceStack));
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("ghostofyou.banish.success"),
                    true);
            if (!player.isCreative()) {
                stack.hurtAndBreak(1, player,
                        p -> p.broadcastBreakEvent(player.getUsedItemHand()));
            }
            player.getCooldowns().addCooldown(stack.getItem(), 100);
        });
    }

    private static ItemStack buildEssenceStack(String ownerName, String deathCause,
                                               String killerName, long livedMinutes,
                                               long banishedAt, @org.jetbrains.annotations.Nullable net.minecraft.nbt.CompoundTag recordingNbt) {
        ItemStack essence = new ItemStack(
                com.coderaiderscdr.ghostofyou.item.ModItems.GHOST_ESSENCE.get());
        CompoundTag tag = essence.getOrCreateTag();
        tag.putString("OwnerName",    ownerName);
        tag.putString("DeathCause",   deathCause.isEmpty() ? "unknown" : deathCause);
        tag.putString("KillerName",   killerName);
        tag.putLong  ("LivedMinutes", livedMinutes);
        tag.putLong  ("BanishedAt",   banishedAt);
        if (recordingNbt != null) {
            tag.put("Recording", recordingNbt);
        }
        return essence;
    }

    // ------------------------------------------------------------------
    // Banishment visual effect
    // ------------------------------------------------------------------

    /** Phase 1: soul-fire spiral + imploding sphere + lightning bolt + layered sounds. */
    private static void playBanishmentPhase1(ServerLevel level, double x, double y, double z) {
        // Spiral of soul-fire flames rising upward
        for (int i = 0; i < 80; i++) {
            double angle  = i * 0.3;
            double radius = 0.5 + i * 0.02;
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            double dy = i * 0.03;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x + dx, y + dy, z + dz, 1, 0, 0, 0, 0.0);
        }

        // Inward-collapsing sphere of soul particles
        for (int i = 0; i < 60; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi   = Math.random() * Math.PI;
            double r     = 2.0;
            double dx    = r * Math.sin(phi) * Math.cos(theta);
            double dy    = r * Math.cos(phi) + 1.0;
            double dz    = r * Math.sin(phi) * Math.sin(theta);
            level.sendParticles(ParticleTypes.SOUL,
                    x + dx, y + dy, z + dz,
                    1, -dx * 0.1, -dy * 0.1, -dz * 0.1, 0.05);
        }

        // Visual lightning bolt (no fire / damage)
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(x, y, z);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }

        // Layered sounds: wither death + wither moan + thunder + beacon off
        level.playSound(null, x, y, z, SoundEvents.WITHER_DEATH,
                SoundSource.HOSTILE, 0.6f, 1.3f);
        level.playSound(null, x, y, z, SoundEvents.WITHER_AMBIENT,
                SoundSource.HOSTILE, 1.0f, 0.7f);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER,  0.4f, 1.5f);
        level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE,
                SoundSource.HOSTILE, 1.2f, 0.6f);
    }

    /** Phase 2: vertical end-rod column (soul beam rising to sky). */
    private static void playBanishmentPhase2(ServerLevel level, double x, double y, double z) {
        for (int i = 0; i < 30; i++) {
            level.sendParticles(ParticleTypes.END_ROD,
                    x, y + i * 0.3, z, 1, 0.05, 0, 0.05, 0.0);
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * Return the UUID of the ghost being banished by this player, or
     * {@code null} if none.
     */
    @Nullable
    public static UUID getTargetGhostId(UUID playerId) {
        return activeBanishings.get(playerId);
    }

    /** Whether the player is currently channeling a banish. */
    public static boolean isBanishing(UUID playerId) {
        return activeBanishings.containsKey(playerId);
    }
}
