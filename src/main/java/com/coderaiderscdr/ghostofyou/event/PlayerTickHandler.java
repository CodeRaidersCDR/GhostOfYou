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

public class PlayerTickHandler {

    private static final Map<UUID, PlayerRecorder> RECORDERS = new ConcurrentHashMap<>();

    private PlayerTickHandler() {}

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

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            RECORDERS.put(newPlayer.getUUID(), new PlayerRecorder(newPlayer));
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() && event.getEntity() instanceof ServerPlayer newPlayer) {
            RECORDERS.put(newPlayer.getUUID(), new PlayerRecorder(newPlayer));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        PlayerRecorder recorder = RECORDERS.get(event.player.getUUID());
        if (recorder != null) {
            recorder.tick();
        }
    }

    @Nullable
    public static PlayerRecorder getRecorder(UUID playerId) {
        return RECORDERS.get(playerId);
    }

    public static Collection<PlayerRecorder> getAllRecorders() {
        return Collections.unmodifiableCollection(RECORDERS.values());
    }
}
