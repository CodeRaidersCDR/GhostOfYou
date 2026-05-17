package com.coderaiderscdr.ghostofyou.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
     * Complete a banishing: remove the ghost, drop essence, damage item.
     *
     * @param player     the player
     * @param stack      the Ghost Banisher item stack
     */
    public static void completeBanishing(Player player, ItemStack stack) {
        UUID ghostId = activeBanishings.remove(player.getUUID());
        if (ghostId == null) return;

        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.world.entity.Entity entity = sl.getEntity(ghostId);
            if (entity instanceof GhostEntity ghost && ghost.isAlive()) {
                // Drop Ghost Essence
                net.minecraft.world.item.ItemStack essence =
                        new net.minecraft.world.item.ItemStack(
                                com.coderaiderscdr.ghostofyou.item.ModItems.GHOST_ESSENCE.get());
                // Write ghost metadata into NBT
                net.minecraft.nbt.CompoundTag meta = new net.minecraft.nbt.CompoundTag();
                meta.putString("ghostName",        ghost.getOwnerName());
                meta.putString("deathCauseKey",    "unknown");
                long lifespan = (sl.getGameTime() - ghost.getCreationTime()) / 1200L; // minutes
                meta.putLong  ("lifespanMinutes",  lifespan);
                essence.setTag(meta);

                ghost.spawnAtLocation(essence);
                ghost.discard();

                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("ghostofyou.banish.success"),
                        true);

                // Damage the banisher
                if (!player.isCreative()) {
                    stack.hurtAndBreak(1, player,
                            p -> p.broadcastBreakEvent(player.getUsedItemHand()));
                }
            }
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
