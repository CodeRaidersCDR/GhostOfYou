package com.coderaiderscdr.ghostofyou.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Dedicated category loggers for Ghost of You.
 *
 * Filter the Forge console / latest.log with:
 *   grep "GhostOfYou/"
 *
 * Categories:
 *   GhostOfYou/Main       — mod boot, registration
 *   GhostOfYou/Playback   — per-tick playback (DEBUG only)
 *   GhostOfYou/Recording  — frame capture, buffer state
 *   GhostOfYou/Spawn      — ghost / crystal spawn / despawn
 *   GhostOfYou/Lifecycle  — config reload, world load
 */
public final class ModLogger {
    public static final Logger MAIN      = LogManager.getLogger("GhostOfYou/Main");
    public static final Logger PLAYBACK  = LogManager.getLogger("GhostOfYou/Playback");
    public static final Logger RECORDING = LogManager.getLogger("GhostOfYou/Recording");
    public static final Logger SPAWN     = LogManager.getLogger("GhostOfYou/Spawn");
    public static final Logger LIFECYCLE = LogManager.getLogger("GhostOfYou/Lifecycle");

    private ModLogger() {}
}
