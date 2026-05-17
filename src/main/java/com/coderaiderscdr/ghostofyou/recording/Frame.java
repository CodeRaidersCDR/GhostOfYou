package com.coderaiderscdr.ghostofyou.recording;

import java.nio.ByteBuffer;

/**
 * Static helpers for reading and writing 28-byte frames to/from a ByteBuffer.
 *
 * <p>Frame layout (exactly 28 bytes):
 * <pre>
 * Offset | Size | Type   | Field
 * -------+------+--------+--------------------------------
 *   0-1  |  2   | short  | tickDelta (ticks since prev frame)
 *   2-5  |  4   | float  | deltaX (relative to prev frame)
 *   6-9  |  4   | float  | deltaZ
 *  10-13 |  4   | float  | deltaY
 *  14-15 |  2   | short  | yaw × 100 (0.01° precision)
 *  16-17 |  2   | short  | pitch × 100
 *   18   |  1   | byte   | flags
 *   19   |  1   | byte   | heldItemSlot (0-8)
 *  20-23 |  4   | int    | actionEventId (0=none)
 *  24-27 |  4   | int    | blockStateId (for break/place, else 0)
 * </pre>
 */
public final class Frame {

    /** Exact size of one frame in bytes. */
    public static final int BYTES = 28;

    // Flags bit masks
    public static final byte FLAG_SNEAK      = (byte) (1 << 0);
    public static final byte FLAG_SPRINT     = (byte) (1 << 1);
    public static final byte FLAG_SWIM       = (byte) (1 << 2);
    public static final byte FLAG_FLY        = (byte) (1 << 3);
    public static final byte FLAG_ON_GROUND  = (byte) (1 << 4);
    public static final byte FLAG_USING_ITEM = (byte) (1 << 5);
    public static final byte FLAG_ATTACK     = (byte) (1 << 6);
    public static final byte FLAG_ON_FIRE    = (byte) (1 << 7);
    /** @deprecated bit 7 is now used for recorded fire visuals. */
    @Deprecated
    public static final byte FLAG_HURT       = FLAG_ON_FIRE;

    private Frame() {}

    // ------------------------------------------------------------------
    // Write — uses absolute byte addresses, safe on any ByteBuffer
    // ------------------------------------------------------------------

    /**
     * Write a complete frame at slot {@code index} (0-based) of the buffer.
     * Uses absolute addressing so the buffer's current position is never modified.
     */
    public static void write(ByteBuffer buf, int index,
                             short tickDelta, float deltaX, float deltaZ, float deltaY,
                             short yawCenti, short pitchCenti, byte flags,
                             byte heldSlot, int actionEventId, int blockStateId) {
        final int base = index * BYTES;
        buf.putShort(base,      tickDelta);
        buf.putFloat(base + 2,  deltaX);
        buf.putFloat(base + 6,  deltaZ);
        buf.putFloat(base + 10, deltaY);
        buf.putShort(base + 14, yawCenti);
        buf.putShort(base + 16, pitchCenti);
        buf.put    (base + 18,  flags);
        buf.put    (base + 19,  heldSlot);
        buf.putInt (base + 20,  actionEventId);
        buf.putInt (base + 24,  blockStateId);
    }

    // ------------------------------------------------------------------
    // Read — absolute addressing, no position side-effects
    // ------------------------------------------------------------------

    public static short readTickDelta(ByteBuffer buf, int index)      { return buf.getShort(index * BYTES); }
    public static float readDeltaX   (ByteBuffer buf, int index)      { return buf.getFloat(index * BYTES + 2); }
    public static float readDeltaZ   (ByteBuffer buf, int index)      { return buf.getFloat(index * BYTES + 6); }
    public static float readDeltaY   (ByteBuffer buf, int index)      { return buf.getFloat(index * BYTES + 10); }
    public static short readYawCenti (ByteBuffer buf, int index)      { return buf.getShort(index * BYTES + 14); }
    public static short readPitchCenti(ByteBuffer buf, int index)     { return buf.getShort(index * BYTES + 16); }
    public static byte  readFlags    (ByteBuffer buf, int index)      { return buf.get(index * BYTES + 18); }
    public static byte  readHeldSlot (ByteBuffer buf, int index)      { return buf.get(index * BYTES + 19); }
    public static int   readActionEventId(ByteBuffer buf, int index)  { return buf.getInt(index * BYTES + 20); }
    public static int   readBlockStateId (ByteBuffer buf, int index)  { return buf.getInt(index * BYTES + 24); }
}
