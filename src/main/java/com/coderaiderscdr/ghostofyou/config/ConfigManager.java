package com.coderaiderscdr.ghostofyou.config;

/**
 * Convenience façade for reading config values.
 * Centralises all config access so that call sites don't need to import both
 * ModConfig.COMMON and ModConfig.CLIENT.
 */
public final class ConfigManager {

    private ConfigManager() {}

    // ------------------------------------------------------------------
    // Common / server-side
    // ------------------------------------------------------------------

    public static boolean isRecordingEnabled() {
        return ModConfig.COMMON.enableRecording.get();
    }

    public static boolean isGhostSpawningEnabled() {
        return ModConfig.COMMON.enableGhostSpawning.get();
    }

    public static boolean isPlaybackPaused() {
        return ModConfig.COMMON.pauseAllPlayback.get();
    }

    public static int recordingDurationSeconds() {
        return ModConfig.COMMON.recordingDurationSeconds.get();
    }

    public static int sampleIntervalTicks() {
        return ModConfig.COMMON.sampleIntervalTicks.get();
    }

    public static int maxGhostsPerChunk() {
        return ModConfig.COMMON.maxGhostsPerChunk.get();
    }

    public static int maxGhostsPerPlayer() {
        return ModConfig.COMMON.maxGhostsPerPlayer.get();
    }

    public static int playbackLoopDelayTicks() {
        return ModConfig.COMMON.playbackLoopDelayTicks.get();
    }

    public static boolean recordBlockActions() {
        return ModConfig.COMMON.recordBlockActions.get();
    }

    public static boolean recordCombat() {
        return ModConfig.COMMON.recordCombat.get();
    }

    // ------------------------------------------------------------------
    // Client-side
    // ------------------------------------------------------------------

    public static float ghostTransparency() {
        return ModConfig.CLIENT.ghostTransparency.get().floatValue();
    }

    public static boolean isGhostGlowing() {
        return ModConfig.CLIENT.ghostGlowing.get();
    }

    public static int renderDistance() {
        return ModConfig.CLIENT.renderDistance.get();
    }

    public static boolean isFrustumCullingEnabled() {
        return ModConfig.CLIENT.enableFrustumCulling.get();
    }

    public static int lodNearDistance() {
        return ModConfig.COMMON.lodNearDistance.get();
    }

    public static int lodFarDistance() {
        return ModConfig.COMMON.lodFarDistance.get();
    }

    public static int lodFreezeDistance() {
        return ModConfig.COMMON.lodFreezeDistance.get();
    }

    public static boolean enablePerformanceProfiler() {
        return ModConfig.COMMON.enablePerformanceProfiler.get();
    }
}
