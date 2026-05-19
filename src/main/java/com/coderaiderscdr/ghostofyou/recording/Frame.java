package com.coderaiderscdr.ghostofyou.recording;

import java.nio.ByteBuffer;

public final class Frame {

    public static final int BYTES = 28;

    public static final byte FLAG_SNEAK      = (byte) (1 << 0);
    public static final byte FLAG_SPRINT     = (byte) (1 << 1);
    public static final byte FLAG_SWIM       = (byte) (1 << 2);
    public static final byte FLAG_FLY        = (byte) (1 << 3);
    public static final byte FLAG_ON_GROUND  = (byte) (1 << 4);
    public static final byte FLAG_USING_ITEM = (byte) (1 << 5);
    public static final byte FLAG_ATTACK     = (byte) (1 << 6);
    public static final byte FLAG_HURT       = (byte) (1 << 7);
    public static final byte FLAG_ON_FIRE    = FLAG_HURT;

    private Frame() {}

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
