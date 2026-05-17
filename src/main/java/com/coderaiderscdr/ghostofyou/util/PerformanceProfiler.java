package com.coderaiderscdr.ghostofyou.util;

import com.coderaiderscdr.ghostofyou.GhostOfYou;

import java.util.DoubleSummaryStatistics;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight performance profiler for the Ghost of You mod.
 *
 * <p>Enabled via {@code enablePerformanceProfiler = true} in the common config.
 * Reports aggregate statistics to the log every 200 ticks (≈10 seconds).
 */
public final class PerformanceProfiler {

    private static final int REPORT_INTERVAL_TICKS = 200;

    private static long ghostTickTotalNs = 0L;
    private static int  ghostTickSamples = 0;
    private static long ghostTickMaxNs   = 0L;
    private static int  tickCount        = 0;

    private PerformanceProfiler() {}

    /**
     * Record a ghost-tick measurement.
     *
     * @param elapsedNs elapsed time in nanoseconds for the full ghost-tick pass
     */
    public static void recordGhostTick(long elapsedNs) {
        ghostTickTotalNs += elapsedNs;
        ghostTickSamples++;
        if (elapsedNs > ghostTickMaxNs) ghostTickMaxNs = elapsedNs;

        if (++tickCount >= REPORT_INTERVAL_TICKS) {
            report();
            reset();
        }
    }

    private static void report() {
        if (ghostTickSamples == 0) return;
        double avgMs  = ghostTickTotalNs / ghostTickSamples / 1_000_000.0;
        double maxMs  = ghostTickMaxNs / 1_000_000.0;
        GhostOfYou.LOGGER.info(
                "[GhostOfYou][Profiler] ghost-tick over {} samples: avg={:.3f}ms max={:.3f}ms",
                ghostTickSamples, avgMs, maxMs);
    }

    private static void reset() {
        ghostTickTotalNs = 0L;
        ghostTickSamples = 0;
        ghostTickMaxNs   = 0L;
        tickCount        = 0;
    }
}
