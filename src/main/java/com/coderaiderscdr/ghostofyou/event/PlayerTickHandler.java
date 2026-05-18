package com.coderaiderscdr.ghostofyou.event;

import com.coderaiderscdr.ghostofyou.recording.PlayerRecorder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the per-player {@link PlayerRecorder} lifecycle and drives recorder ticks.
 *
 * <p>One recorder is created when the player logs in and destroyed on logout.
 * Recorders are ticked on the server side in {@link TickEvent.PlayerTickEvent}.
 */
public class PlayerTickHandler {

    /** Server-side map: player UUID → active recorder. */
    private static final Map<UUID, PlayerRecorder> RECORDERS = new ConcurrentHashMap<>();

    private PlayerTickHandler() {}

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RECORDERS.put(player.getUUID(), new PlayerRecorder(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECORDERS.remove(event.getEntity().getUUID());
    }

    /**
     * On death-respawn the old ServerPlayer instance is discarded and a new one
     * is created. Replace the recorder with a fresh one pointing to the new instance
     * so {@code player.getX()} always returns the LIVE position, not the corpse pos.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            RECORDERS.put(newPlayer.getUUID(), new PlayerRecorder(newPlayer));
        }
    }

    /**
     * On dimension change (not death) the player entity is also replaced.
     * Swap the recorder reference so deltas continue to be correct.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() && event.getEntity() instanceof ServerPlayer newPlayer) {
            RECORDERS.put(newPlayer.getUUID(), new PlayerRecorder(newPlayer));
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /**
     * Drive all active recorders once per server tick.
     * HOT PATH — called 20 × per second per online player.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        PlayerRecorder recorder = RECORDERS.get(event.player.getUUID());
        if (recorder != null) {
            recorder.tick();
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * Get the recorder for a player UUID, or {@code null} if the player
     * is not currently online.
     */
    @Nullable
    public static PlayerRecorder getRecorder(UUID playerId) {
        return RECORDERS.get(playerId);
    }

    /** Unmodifiable snapshot of all active recorders. */
    public static Collection<PlayerRecorder> getAllRecorders() {
        return Collections.unmodifiableCollection(RECORDERS.values());
    }
}
