package com.coderaiderscdr.ghostofyou.event;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.util.PerformanceProfiler;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives server-side performance profiling for Ghost of You.
 *
 * <p>Ghost playback LOD is now handled inside {@link com.coderaiderscdr.ghostofyou.entity.GhostEntity#tick()}
 * directly, so this handler only runs the optional perf profiler.
 */
public class ServerTickHandler {

    private ServerTickHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (ConfigManager.isPlaybackPaused()) return;

        if (ConfigManager.enablePerformanceProfiler()) {
            long start = System.nanoTime();
            // Ghost ticks are now driven by GhostEntity.tick() — nothing to measure here.
            PerformanceProfiler.recordGhostTick(System.nanoTime() - start);
        }
    }
}
