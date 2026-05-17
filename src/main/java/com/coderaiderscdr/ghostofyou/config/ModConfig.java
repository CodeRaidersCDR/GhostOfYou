package com.coderaiderscdr.ghostofyou.config;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Forge Config API wrapper for Ghost of You.
 * Two configs: common (server-side, synced) and client (client-only).
 */
public class ModConfig {

    // -------------------------------------------------------------------------
    // Common config (ghostofyou-common.toml)
    // -------------------------------------------------------------------------

    public static final CommonConfig COMMON;
    static final ForgeConfigSpec COMMON_SPEC;

    // -------------------------------------------------------------------------
    // Client config (ghostofyou-client.toml)
    // -------------------------------------------------------------------------

    public static final ClientConfig CLIENT;
    static final ForgeConfigSpec CLIENT_SPEC;

    static {
        final Pair<CommonConfig, ForgeConfigSpec> commonPair =
                new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON_SPEC = commonPair.getRight();
        COMMON = commonPair.getLeft();

        final Pair<ClientConfig, ForgeConfigSpec> clientPair =
                new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    /**
     * Register configs with the mod loading context.
     *
     * @param ctx the mod loading context
     */
    public static void register(ModLoadingContext ctx) {
        ctx.registerConfig(Type.COMMON, COMMON_SPEC, GhostOfYou.MOD_ID + "-common.toml");
        ctx.registerConfig(Type.CLIENT, CLIENT_SPEC, GhostOfYou.MOD_ID + "-client.toml");
    }

    // =========================================================================
    // Common config class
    // =========================================================================

    public static final class CommonConfig {

        // [general]
        public final ForgeConfigSpec.BooleanValue enableRecording;
        public final ForgeConfigSpec.BooleanValue enableGhostSpawning;
        public final ForgeConfigSpec.BooleanValue showExistingGhostsWhenDisabled;
        public final ForgeConfigSpec.BooleanValue pauseAllPlayback;

        // [recording]
        public final ForgeConfigSpec.IntValue recordingDurationSeconds;
        public final ForgeConfigSpec.IntValue sampleIntervalTicks;
        public final ForgeConfigSpec.BooleanValue recordBlockActions;
        public final ForgeConfigSpec.BooleanValue recordCombat;

        // [ghosts]
        public final ForgeConfigSpec.IntValue maxGhostsPerChunk;
        public final ForgeConfigSpec.IntValue maxGhostsPerPlayer;
        public final ForgeConfigSpec.IntValue playbackLoopDelayTicks;

        // [performance] — server-side LOD settings
        public final ForgeConfigSpec.IntValue lodNearDistance;
        public final ForgeConfigSpec.IntValue lodFarDistance;
        public final ForgeConfigSpec.IntValue lodFreezeDistance;
        public final ForgeConfigSpec.BooleanValue enablePerformanceProfiler;

        CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            enableRecording = builder
                    .comment("Enable or disable player movement recording.")
                    .define("enableRecording", true);
            enableGhostSpawning = builder
                    .comment("Enable or disable ghost spawning on player death.")
                    .define("enableGhostSpawning", true);
            showExistingGhostsWhenDisabled = builder
                    .comment("Show existing ghosts even when ghost spawning is disabled.")
                    .define("showExistingGhostsWhenDisabled", false);
            pauseAllPlayback = builder
                    .comment("Pause all ghost playback globally.")
                    .define("pauseAllPlayback", false);
            builder.pop();

            builder.push("recording");
            recordingDurationSeconds = builder
                    .comment("Duration in seconds to keep in the recording buffer (30–900).")
                    .defineInRange("recordingDurationSeconds", 300, 30, 900);
            sampleIntervalTicks = builder
                    .comment("Record one frame every N ticks (1–20). Lower = smoother, more memory.")
                    .defineInRange("sampleIntervalTicks", 4, 1, 20);
            recordBlockActions = builder
                    .comment("Record block break/place events.")
                    .define("recordBlockActions", true);
            recordCombat = builder
                    .comment("Record combat events.")
                    .define("recordCombat", true);
            builder.pop();

            builder.push("ghosts");
            maxGhostsPerChunk = builder
                    .comment("Maximum ghosts per chunk. Oldest removed first when exceeded (1–20).")
                    .defineInRange("maxGhostsPerChunk", 5, 1, 20);
            maxGhostsPerPlayer = builder
                    .comment("Maximum ghosts per player across the dimension. FIFO eviction (1–200).")
                    .defineInRange("maxGhostsPerPlayer", 50, 1, 200);
            playbackLoopDelayTicks = builder
                    .comment("Ticks to pause between loop repetitions (0–200).")
                    .defineInRange("playbackLoopDelayTicks", 60, 0, 200);
            builder.pop();

            builder.push("performance");
            lodNearDistance = builder
                    .comment("Distance (blocks) for full-rate LOD ticking.")
                    .defineInRange("lodNearDistance", 32, 8, 64);
            lodFarDistance = builder
                    .comment("Distance (blocks) for reduced-rate LOD ticking.")
                    .defineInRange("lodFarDistance", 64, 32, 128);
            lodFreezeDistance = builder
                    .comment("Distance (blocks) beyond which ghosts are frozen.")
                    .defineInRange("lodFreezeDistance", 128, 64, 512);
            enablePerformanceProfiler = builder
                    .comment("Enable the internal performance profiler (for debugging).")
                    .define("enablePerformanceProfiler", false);
            builder.pop();
        }
    }

    // =========================================================================
    // Client config class
    // =========================================================================

    public static final class ClientConfig {

        // [rendering]
        public final ForgeConfigSpec.DoubleValue ghostTransparency;
        public final ForgeConfigSpec.BooleanValue ghostGlowing;
        public final ForgeConfigSpec.IntValue renderDistance;
        public final ForgeConfigSpec.BooleanValue enableFrustumCulling;

        ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.push("rendering");
            ghostTransparency = builder
                    .comment("Ghost alpha transparency (0.1 = nearly invisible, 1.0 = fully opaque).")
                    .defineInRange("ghostTransparency", 0.6, 0.1, 1.0);
            ghostGlowing = builder
                    .comment("Show a glowing outline on ghosts.")
                    .define("ghostGlowing", true);
            renderDistance = builder
                    .comment("Distance in blocks at which ghosts are rendered (8–128).")
                    .defineInRange("renderDistance", 48, 8, 128);
            enableFrustumCulling = builder
                    .comment("Skip rendering ghosts outside the camera frustum.")
                    .define("enableFrustumCulling", true);
            builder.pop();
        }
    }
}
