package com.coderaiderscdr.ghostofyou.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

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
