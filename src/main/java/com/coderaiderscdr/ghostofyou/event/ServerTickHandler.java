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

/**
 * Drives ghost playback with LOD-based tick scheduling.
 *
 * <p>LOD zones per config (default):
 * <ul>
 *   <li>&lt; 32 blocks from any player — tick every game tick</li>
 *   <li>32 – 64 blocks — tick every 4 ticks</li>
 *   <li>64 – 128 blocks — tick every 10 ticks</li>
 *   <li>&gt; 128 blocks — frozen (no tick, render only)</li>
 * </ul>
 *
 * Ghosts in unloaded chunks are skipped entirely.
 */
public class ServerTickHandler {

    private ServerTickHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
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

    // ------------------------------------------------------------------
    // Per-level processing
    // ------------------------------------------------------------------

    /** HOT PATH — called once per level per server tick. */
    private static void tickGhostsInLevel(ServerLevel level) {
        long gameTick = level.getGameTime();

        int nearDist   = ConfigManager.lodNearDistance();
        int midDist    = ConfigManager.lodFarDistance();
        int freezeDist = ConfigManager.lodFreezeDistance();

        // Collect all living ghost entities in this level using entity iteration
        java.util.List<GhostEntity> ghosts = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof GhostEntity g && g.isAlive()) ghosts.add(g);
        }

        for (GhostEntity ghost : ghosts) { // HOT PATH
            // Compute nearest-player distance (or zero if no players in this level)
            double minDistSq = Double.MAX_VALUE;
            for (Player player : level.players()) {
                double dsq = player.distanceToSqr(ghost);
                if (dsq < minDistSq) minDistSq = dsq;
            }

            int nearSq   = nearDist   * nearDist;
            int midSq    = midDist    * midDist;
            int freezeSq = freezeDist * freezeDist;

            if (minDistSq <= nearSq) {
                // Near zone — tick every game tick
                ghost.tickPlayback();
            } else if (minDistSq <= midSq) {
                // Mid zone — tick every 4 ticks
                if (gameTick % 4 == 0) ghost.tickPlayback();
            } else if (minDistSq <= freezeSq) {
                // Far zone — tick every 10 ticks
                if (gameTick % 10 == 0) ghost.tickPlayback();
            } else {
                // Beyond freeze distance (or no players in level) — tick every 20 ticks
                // Ghosts NEVER fully stop: this ensures playback continues even when
                // the player respawns far away, so the ghost is always at the right
                // position when the player walks back.
                if (gameTick % 20 == 0) ghost.tickPlayback();
            }
        }
    }
}
