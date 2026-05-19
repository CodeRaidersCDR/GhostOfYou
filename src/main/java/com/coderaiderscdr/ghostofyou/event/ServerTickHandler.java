package com.coderaiderscdr.ghostofyou.event;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.coderaiderscdr.ghostofyou.util.PerformanceProfiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayDeque;
import java.util.Queue;

public class ServerTickHandler {

    private ServerTickHandler() {}

    private record DelayedTask(long fireTick, Runnable action) {}
    private static final Queue<DelayedTask> DELAYED_TASKS = new ArrayDeque<>();

    public static void scheduleDelayed(MinecraftServer server, int delayTicks, Runnable action) {
        if (server == null) return;
        DELAYED_TASKS.add(new DelayedTask((long) server.getTickCount() + delayTicks, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        if (!DELAYED_TASKS.isEmpty()) {
            long now = server.getTickCount();
            DELAYED_TASKS.removeIf(task -> {
                if (task.fireTick() <= now) {
                    try { task.action().run(); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            });
        }

        if (ConfigManager.isPlaybackPaused()) return;

        boolean profiling = ConfigManager.enablePerformanceProfiler();

        long start = profiling ? System.nanoTime() : 0L;

        for (ServerLevel level : server.getAllLevels()) {
            tickGhostsInLevel(level);
        }

        if (profiling) {
            long elapsed = System.nanoTime() - start;
            PerformanceProfiler.recordGhostTick(elapsed);
        }
    }

    private static void tickGhostsInLevel(ServerLevel level) {
        long gameTick = level.getGameTime();

        int nearDist   = ConfigManager.lodNearDistance();
        int midDist    = ConfigManager.lodFarDistance();
        int freezeDist = ConfigManager.lodFreezeDistance();

        java.util.List<GhostEntity> ghosts = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof GhostEntity g && g.isAlive()) ghosts.add(g);
        }

        for (GhostEntity ghost : ghosts) {

            double minDistSq = Double.MAX_VALUE;
            for (Player player : level.players()) {
                double dsq = player.distanceToSqr(ghost);
                if (dsq < minDistSq) minDistSq = dsq;
            }

            int nearSq   = nearDist   * nearDist;
            int midSq    = midDist    * midDist;
            int freezeSq = freezeDist * freezeDist;

            if (minDistSq <= nearSq) {

                ghost.tickPlayback(1);
            } else if (minDistSq <= midSq) {

                if (gameTick % 4 == 0) ghost.tickPlayback(4);
            } else if (minDistSq <= freezeSq) {

                if (gameTick % 10 == 0) ghost.tickPlayback(10);
            } else {

                if (gameTick % 20 == 0) ghost.tickPlayback(20);
            }
        }
    }
}
