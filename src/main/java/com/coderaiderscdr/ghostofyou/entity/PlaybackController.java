package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.recording.Frame;
import com.coderaiderscdr.ghostofyou.util.ModLogger;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Controls the server-side playback of a ghost's recorded frames.
 *
 * <p>The controller replays frames in the same direction they were recorded:
 * oldest retained frame to newest/death frame. Frame deltas are stretched over
 * their recorded tickDelta so walking, falling, and burning loops play at the
 * same pace the player experienced.
 */
public class PlaybackController {

    /** Ticks to wait at the death position before restarting the loop. */
    private static final int LOOP_DELAY_TICKS = 60;

    /** Read-only heap buffer wrapping the serialised frame bytes. */
    private final ByteBuffer frames;
    private final int frameCount;

    /** Absolute world coordinates of the oldest retained sample. */
    private final double startX, startY, startZ;

    /**
     * First frame index that has noticeable movement (XZ >= 0.05 or Y >= 0.05 blocks).
     * Leading stationary frames (player was standing still before the fatal fall) are
     * skipped so the ghost immediately starts moving when the loop begins/resets.
     */
    private final int    loopStartFrame;
    private final double loopStartX, loopStartY, loopStartZ;

    /** Running absolute position during playback. */
    private double currentX, currentY, currentZ;

    /** Frame cursor for forward playback, oldest -> newest. */
    private int currentFrame;
    private int frameTick;
    private double frameStartX, frameStartY, frameStartZ;
    private double frameTargetX, frameTargetY, frameTargetZ;

    /** Ticks waited since last loop reset. */
    private int loopDelayCounter = 0;

    /**
     * Create a controller from raw, ordered frame bytes.
     *
     * @param rawFrameBytes bytes exported from the circular frame buffer
     * @param deathX        absolute X coordinate where the player died
     * @param deathY        absolute Y coordinate where the player died
     * @param deathZ        absolute Z coordinate where the player died
     */
    public PlaybackController(byte[] rawFrameBytes, double deathX, double deathY, double deathZ) {
        if (rawFrameBytes.length < Frame.BYTES * 2) {
            byte[] padded = new byte[Frame.BYTES * 2];
            if (rawFrameBytes.length >= Frame.BYTES) {
                System.arraycopy(rawFrameBytes, 0, padded, 0, Frame.BYTES);
                System.arraycopy(rawFrameBytes, 0, padded, Frame.BYTES, Frame.BYTES);
                ByteBuffer.wrap(padded).putShort(Frame.BYTES, (short) 20);
            } else {
                ByteBuffer paddedBuffer = ByteBuffer.wrap(padded);
                paddedBuffer.putShort(0, (short) 20);
                paddedBuffer.putShort(Frame.BYTES, (short) 20);
            }
            rawFrameBytes = padded;
        }

        this.frames     = ByteBuffer.wrap(rawFrameBytes);
        this.frameCount = rawFrameBytes.length / Frame.BYTES;

        double totalDx = 0.0;
        double totalDy = 0.0;
        double totalDz = 0.0;
        for (int i = 0; i < frameCount; i++) {
            totalDx += Frame.readDeltaX(frames, i);
            totalDy += Frame.readDeltaY(frames, i);
            totalDz += Frame.readDeltaZ(frames, i);
        }

        this.startX = deathX - totalDx;
        this.startY = deathY - totalDy;
        this.startZ = deathZ - totalDz;

        // Scan for the first frame with noticeable movement so the playback loop
        // skips any leading "standing still" portion.  This fixes the case where
        // the player stood motionless for most of the recording window and then
        // died from a fall — without the skip the ghost visually stands in place
        // at the cliff edge for most of the loop before falling.
        {
            int   firstMoving = 0;
            double lsX = startX, lsY = startY, lsZ = startZ;
            double accumX = startX, accumY = startY, accumZ = startZ;
            for (int i = 0; i < frameCount; i++) {
                float fdx = Frame.readDeltaX(frames, i);
                float fdy = Frame.readDeltaY(frames, i);
                float fdz = Frame.readDeltaZ(frames, i);
                if (Math.abs(fdx) + Math.abs(fdz) >= 0.05f || Math.abs(fdy) >= 0.05f) {
                    firstMoving = i;
                    lsX = accumX;
                    lsY = accumY;
                    lsZ = accumZ;
                    break;
                }
                accumX += fdx;
                accumY += fdy;
                accumZ += fdz;
            }
            this.loopStartFrame = firstMoving;
            this.loopStartX = lsX;
            this.loopStartY = lsY;
            this.loopStartZ = lsZ;
        }

        this.currentX = loopStartX;
        this.currentY = loopStartY;
        this.currentZ = loopStartZ;
        this.currentFrame = loopStartFrame;
        this.frameTick = 0;
        this.frameStartX = loopStartX;
        this.frameStartY = loopStartY;
        this.frameStartZ = loopStartZ;
        this.frameTargetX = loopStartX;
        this.frameTargetY = loopStartY;
        this.frameTargetZ = loopStartZ;
    }

    /**
     * Advance playback by one game tick and apply the result to {@code ghost}.
     * Must be called server-side only.
     */
    public void tick(GhostEntity ghost) {
        if (frameCount == 0) return;

        if (currentFrame >= frameCount) {
            // Reached end of recording. Pause briefly at the death position, then loop.
            loopDelayCounter++;
            if (loopDelayCounter == 1) {
                ModLogger.PLAYBACK.info("Ghost[{}] reached end of recording ({} frames). Waiting {} ticks before loop.",
                        ghost.getOwnerName(), frameCount, LOOP_DELAY_TICKS);
            }
            if (loopDelayCounter >= LOOP_DELAY_TICKS) {
                ModLogger.PLAYBACK.info("Ghost[{}] LOOP RESET — restarting from ({}, {}, {})",
                        ghost.getOwnerName(), loopStartX, loopStartY, loopStartZ);
                reset(ghost);
            }
            return;
        }

        float dx = Frame.readDeltaX(frames, currentFrame);
        float dy = Frame.readDeltaY(frames, currentFrame);
        float dz = Frame.readDeltaZ(frames, currentFrame);
        short yawCenti   = Frame.readYawCenti(frames, currentFrame);
        short pitchCenti = Frame.readPitchCenti(frames, currentFrame);
        byte flags       = Frame.readFlags(frames, currentFrame);
        int durationTicks = Math.max(1, Frame.readTickDelta(frames, currentFrame));

        if (frameTick == 0) {
            frameStartX = currentX;
            frameStartY = currentY;
            frameStartZ = currentZ;
            frameTargetX = frameStartX + dx;
            frameTargetY = frameStartY + dy;
            frameTargetZ = frameStartZ + dz;
        }

        double previousX = currentX;
        double previousY = currentY;
        double previousZ = currentZ;

        double progress = (frameTick + 1) / (double) durationTicks;
        currentX = lerp(frameStartX, frameTargetX, progress);
        currentY = lerp(frameStartY, frameTargetY, progress);
        currentZ = lerp(frameStartZ, frameTargetZ, progress);

        if (++frameTick >= durationTicks) {
            currentX = frameTargetX;
            currentY = frameTargetY;
            currentZ = frameTargetZ;
            currentFrame++;
            frameTick = 0;
        }

        float yaw = yawCenti / 100.0f;
        float pitch = pitchCenti / 100.0f;

        // moveTo() sets position AND rotation in a way that vanilla properly
        // tracks for client packet generation. setPos() alone does not always
        // mark the entity as moved for packet purposes.
        ghost.moveTo(currentX, currentY, currentZ, yaw, pitch);
        ghost.setYHeadRot(yaw);
        ghost.setYBodyRot(yaw);

        // DO NOT touch xOld/yOld/zOld or yRotO/xRotO here.
        // Vanilla super.tick() will set them BEFORE this tick runs.

        // Delta movement is used by walk animation in GhostEntity.tick()
        ghost.setDeltaMovement(
                currentX - previousX,
                currentY - previousY,
                currentZ - previousZ);

        ghost.setShiftKeyDown((flags & Frame.FLAG_SNEAK) != 0);
        ghost.setSprinting((flags & Frame.FLAG_SPRINT) != 0);
        ghost.setSwimming((flags & Frame.FLAG_SWIM) != 0);
        ghost.setOnGround((flags & Frame.FLAG_ON_GROUND) != 0);
        ghost.applyRecordedPose(flags);

        boolean onFire = (flags & Frame.FLAG_ON_FIRE) != 0;
        ghost.setSharedFlagOnFire(onFire);
        ghost.setRemainingFireTicks(onFire ? 20 : 0);
    }

    private void reset(GhostEntity ghost) {
        currentFrame = loopStartFrame;
        frameTick = 0;
        loopDelayCounter = 0;
        currentX = loopStartX;
        currentY = loopStartY;
        currentZ = loopStartZ;
        frameStartX = loopStartX;
        frameStartY = loopStartY;
        frameStartZ = loopStartZ;
        frameTargetX = loopStartX;
        frameTargetY = loopStartY;
        frameTargetZ = loopStartZ;
        ghost.moveTo(loopStartX, loopStartY, loopStartZ, ghost.getYRot(), ghost.getXRot());
        ghost.setDeltaMovement(0.0, 0.0, 0.0);
    }

    /** The position where the ghost's playback loop begins (first frame with movement). */
    public double[] getStartPosition() {
        return new double[]{ loopStartX, loopStartY, loopStartZ };
    }

    public int getFrameCount() { return frameCount; }

    public int getCurrentFrame() { return currentFrame; }

    private static double lerp(double start, double end, double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return start + (end - start) * clamped;
    }

    /**
     * Save the raw frame bytes (GZIP-compressed) plus the death position into a
     * tag so the controller can be reconstructed after a server restart.
     */
    public CompoundTag saveToNbt(double deathX, double deathY, double deathZ) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("deathX", deathX);
        tag.putDouble("deathY", deathY);
        tag.putDouble("deathZ", deathZ);

        byte[] raw = frames.array();
        byte[] stored;
        boolean compressed = false;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(raw.length / 3 + 64);
            try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
                gz.write(raw);
            }
            stored = baos.toByteArray();
            compressed = true;
        } catch (IOException e) {
            ModLogger.PLAYBACK.warn("Could not compress ghost recording", e);
            stored = raw;
        }

        tag.putByteArray("frames", stored);
        tag.putBoolean("compressed", compressed);
        return tag;
    }

    /** Restore a controller from a previously saved NBT compound. */
    public static PlaybackController loadFromNbt(CompoundTag tag) {
        double deathX = tag.getDouble("deathX");
        double deathY = tag.getDouble("deathY");
        double deathZ = tag.getDouble("deathZ");

        byte[] stored = tag.getByteArray("frames");
        boolean compressed = tag.getBoolean("compressed");

        byte[] raw;
        if (compressed && stored.length > 0) {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(stored))) {
                raw = gz.readAllBytes();
            } catch (IOException e) {
                ModLogger.PLAYBACK.error("Failed to decompress ghost recording", e);
                raw = stored;
            }
        } else {
            raw = stored;
        }

        return new PlaybackController(raw, deathX, deathY, deathZ);
    }

    /** Estimate memory used by the frame data in bytes. */
    public int frameBytesSize() {
        return frameCount * Frame.BYTES;
    }

    /** Approximate kilobytes used by all frame data. */
    public float kilobytes() {
        return frameCount * Frame.BYTES / 1024f;
    }
}
