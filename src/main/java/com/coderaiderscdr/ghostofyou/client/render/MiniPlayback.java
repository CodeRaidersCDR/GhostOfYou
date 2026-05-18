package com.coderaiderscdr.ghostofyou.client.render;

import com.coderaiderscdr.ghostofyou.recording.Frame;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;

/**
 * Client-side helper that pre-parses a ghost recording into absolute positions.
 * Used by {@link MemorialBlockEntityRenderer} to animate the mini ghost above
 * a bound Memorial Block.
 *
 * <p>The recording's absolute start position is computed using the same
 * formula as {@link com.coderaiderscdr.ghostofyou.entity.PlaybackController}:
 * {@code startX = deathX - sum(all deltaX)}, and each frame accumulates its
 * delta on top.
 *
 * <p>All positions are stored <em>relative to the death point</em> so the
 * renderer can centre the animation independently of world coordinates.
 * They are also clamped/scaled to fit within ±0.4 blocks so the mini ghost
 * never exits the block's visual bounding box.
 */
public final class MiniPlayback {

    /** Visual scale of the mini ghost relative to normal entity size. */
    public static final float MINI_SCALE = 0.28f;

    /**
     * Maximum horizontal/vertical displacement (in ghost-local units) before
     * the position stream is scaled down to fit.
     */
    private static final double MAX_HALF_EXTENT = 0.38;

    // Per-frame data (relative to death position, already scaled)
    /** X position relative to death point, scaled to fit. */
    final float[] relX;
    /** Y position relative to death point, scaled to fit. */
    final float[] relY;
    /** Z position relative to death point, scaled to fit. */
    final float[] relZ;
    /** Yaw in degrees. */
    final float[] yaw;
    /** Flags byte (sneak / swim / fly / etc.). */
    final byte[]  flags;
    /** Cumulative tick at which this frame becomes active. */
    final int[]   tickAtFrame;

    final int frameCount;
    final int totalTicks;

    private MiniPlayback(float[] relX, float[] relY, float[] relZ,
                         float[] yaw, byte[] flags, int[] tickAtFrame) {
        this.relX       = relX;
        this.relY       = relY;
        this.relZ       = relZ;
        this.yaw        = yaw;
        this.flags      = flags;
        this.tickAtFrame = tickAtFrame;
        this.frameCount  = relX.length;
        this.totalTicks  = frameCount > 0 ? tickAtFrame[frameCount - 1] + 1 : 1;
    }

    // ------------------------------------------------------------------
    // Factory
    // ------------------------------------------------------------------

    /**
     * Parse a recording {@link CompoundTag} (as saved by
     * {@code PlaybackController.saveToNbt}) into a {@code MiniPlayback}.
     *
     * @return parsed playback, or {@code null} if the tag is missing/corrupt
     */
    @Nullable
    public static MiniPlayback parse(@Nullable CompoundTag tag) {
        if (tag == null) return null;

        // 1. Decompress frames if needed
        byte[] raw;
        boolean compressed = tag.getBoolean("compressed");
        byte[] stored = tag.getByteArray("frames");
        if (stored.length == 0) return null;

        if (compressed) {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(stored))) {
                raw = gz.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        } else {
            raw = stored;
        }

        int frameCount = raw.length / Frame.BYTES;
        if (frameCount == 0) return null;

        double deathX = tag.getDouble("deathX");
        double deathY = tag.getDouble("deathY");
        double deathZ = tag.getDouble("deathZ");

        ByteBuffer bb = ByteBuffer.wrap(raw);

        // 2. Compute absolute positions (same formula as PlaybackController)
        //    startPos = deathPos - sum(all deltas)
        double sumDx = 0, sumDy = 0, sumDz = 0;
        for (int i = 0; i < frameCount; i++) {
            sumDx += Frame.readDeltaX(bb, i);
            sumDy += Frame.readDeltaY(bb, i);
            sumDz += Frame.readDeltaZ(bb, i);
        }
        double curX = deathX - sumDx;
        double curY = deathY - sumDy;
        double curZ = deathZ - sumDz;

        double[] absX = new double[frameCount];
        double[] absY = new double[frameCount];
        double[] absZ = new double[frameCount];
        for (int i = 0; i < frameCount; i++) {
            curX += Frame.readDeltaX(bb, i);
            curY += Frame.readDeltaY(bb, i);
            curZ += Frame.readDeltaZ(bb, i);
            absX[i] = curX;
            absY[i] = curY;
            absZ[i] = curZ;
        }

        // 3. Convert to positions relative to death point
        float[] rx = new float[frameCount];
        float[] ry = new float[frameCount];
        float[] rz = new float[frameCount];
        for (int i = 0; i < frameCount; i++) {
            rx[i] = (float)(absX[i] - deathX);
            ry[i] = (float)(absY[i] - deathY);
            rz[i] = (float)(absZ[i] - deathZ);
        }

        // 4. Find max extents and scale so the ghost stays within ±MAX_HALF_EXTENT
        double maxExtent = 0.01;
        for (int i = 0; i < frameCount; i++) {
            maxExtent = Math.max(maxExtent, Math.abs(rx[i]));
            maxExtent = Math.max(maxExtent, Math.abs(ry[i]));
            maxExtent = Math.max(maxExtent, Math.abs(rz[i]));
        }
        float posScale = (float)(MAX_HALF_EXTENT / maxExtent);
        if (posScale > 1.0f) posScale = 1.0f; // don't upscale small recordings

        for (int i = 0; i < frameCount; i++) {
            rx[i] *= posScale;
            ry[i] *= posScale;
            rz[i] *= posScale;
        }

        // 5. Read yaw, flags and cumulative tick timestamps
        float[] yaws  = new float[frameCount];
        byte[]  flagsArr = new byte[frameCount];
        int[]   ticks = new int[frameCount];
        int cumTick = 0;
        for (int i = 0; i < frameCount; i++) {
            yaws[i]    = Frame.readYawCenti(bb, i) / 100.0f;
            flagsArr[i] = Frame.readFlags(bb, i);
            int td = Math.max(1, (int) Frame.readTickDelta(bb, i));
            cumTick += td;
            ticks[i] = cumTick;
        }

        return new MiniPlayback(rx, ry, rz, yaws, flagsArr, ticks);
    }

    // ------------------------------------------------------------------
    // Playback query
    // ------------------------------------------------------------------

    /**
     * Returns the frame index active at the given game-tick (looping).
     *
     * @param gameTick  server/client game tick counter
     * @param partial   partial tick in [0,1] for interpolation (unused but kept for API)
     * @return frame index in [0, frameCount)
     */
    public int currentFrame(long gameTick, float partial) {
        if (frameCount == 0) return 0;
        int t = (int)(gameTick % totalTicks);
        // Binary search could be used, but a linear scan is fine for ~200 frames
        for (int i = 0; i < frameCount; i++) {
            if (t <= tickAtFrame[i]) return i;
        }
        return frameCount - 1;
    }
}
