package com.coderaiderscdr.ghostofyou.client.render;

import com.coderaiderscdr.ghostofyou.recording.Frame;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;

public final class MiniPlayback {

    public static final float MINI_SCALE = 0.28f;

    private static final double MAX_HALF_EXTENT = 0.38;

    final float[] relX;

    final float[] relY;

    final float[] relZ;

    final float[] yaw;

    final byte[]  flags;

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

    @Nullable
    public static MiniPlayback parse(@Nullable CompoundTag tag) {
        if (tag == null) return null;

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

        float[] rx = new float[frameCount];
        float[] ry = new float[frameCount];
        float[] rz = new float[frameCount];
        for (int i = 0; i < frameCount; i++) {
            rx[i] = (float)(absX[i] - deathX);
            ry[i] = (float)(absY[i] - deathY);
            rz[i] = (float)(absZ[i] - deathZ);
        }

        double maxExtent = 0.01;
        for (int i = 0; i < frameCount; i++) {
            maxExtent = Math.max(maxExtent, Math.abs(rx[i]));
            maxExtent = Math.max(maxExtent, Math.abs(ry[i]));
            maxExtent = Math.max(maxExtent, Math.abs(rz[i]));
        }
        float posScale = (float)(MAX_HALF_EXTENT / maxExtent);
        if (posScale > 1.0f) posScale = 1.0f;

        for (int i = 0; i < frameCount; i++) {
            rx[i] *= posScale;
            ry[i] *= posScale;
            rz[i] *= posScale;
        }

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

    public int currentFrame(long gameTick, float partial) {
        if (frameCount == 0) return 0;
        int t = (int)(gameTick % totalTicks);

        for (int i = 0; i < frameCount; i++) {
            if (t <= tickAtFrame[i]) return i;
        }
        return frameCount - 1;
    }
}
