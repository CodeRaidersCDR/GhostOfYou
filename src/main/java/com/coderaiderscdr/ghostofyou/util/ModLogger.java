package com.coderaiderscdr.ghostofyou.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Dedicated logger for Ghost of You mod.
 *
 * <p>All log lines are tagged with the {@code GhostOfYou} marker so they can
 * be filtered out of the main Forge log easily:
 * <pre>
 *   tail -f logs/latest.log | grep GhostOfYou
 * </pre>
 *
 * <p>Use sub-loggers for clearer categories:
 * <ul>
 *   <li>{@link #PLAYBACK} — every playback tick, frame movement</li>
 *   <li>{@link #RECORDING} — frame capture, buffer state</li>
 *   <li>{@link #SPAWN} — ghost / crystal spawn events</li>
 *   <li>{@link #LIFECYCLE} — mod load, config reload</li>
 * </ul>
 */
public final class ModLogger {

    public static final Marker MARKER = MarkerManager.getMarker("GhostOfYou");

    public  static final Logger MAIN      = LogManager.getLogger("GhostOfYou");
    public  static final Logger PLAYBACK  = LogManager.getLogger("GhostOfYou/Playback");
    public  static final Logger RECORDING = LogManager.getLogger("GhostOfYou/Recording");
    public  static final Logger SPAWN     = LogManager.getLogger("GhostOfYou/Spawn");
    public  static final Logger LIFECYCLE = LogManager.getLogger("GhostOfYou/Lifecycle");

    private ModLogger() {}

    public static void info(String msg, Object... args)  { MAIN.info(MARKER, msg, args); }
    public static void warn(String msg, Object... args)  { MAIN.warn(MARKER, msg, args); }
    public static void error(String msg, Object... args) { MAIN.error(MARKER, msg, args); }
    public static void debug(String msg, Object... args) { MAIN.debug(MARKER, msg, args); }
}
