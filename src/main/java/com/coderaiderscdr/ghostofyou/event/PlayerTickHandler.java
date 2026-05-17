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
