package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.recording.Frame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;

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

    /** Read-only heap buffer wrapping the serialised frame bytes. */
    private final ByteBuffer frames;
    private final int frameCount;

    /** Absolute world coordinates of the oldest retained sample. */
    private final double startX, startY, startZ;

    /** Running absolute position during playback. */
    private double currentX, currentY, currentZ;

    /** Frame cursor for forward playback, oldest -> newest. */
    private int currentFrame;
    private int frameTick;
    private double frameStartX, frameStartY, frameStartZ;
    private double frameTargetX, frameTargetY, frameTargetZ;

    /** Ticks waited since last loop reset. */
    private int loopDelayCounter = 0;
    private byte previousFlags = 0;

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

        this.currentX = startX;
        this.currentY = startY;
        this.currentZ = startZ;
        this.currentFrame = 0;
        this.frameTick = 0;
        this.frameStartX = startX;
        this.frameStartY = startY;
        this.frameStartZ = startZ;
        this.frameTargetX = startX;
        this.frameTargetY = startY;
        this.frameTargetZ = startZ;
    }

    /**
     * Advance playback by one game tick and apply the result to {@code ghost}.
     * Must be called server-side only.
     */
    public void tick(GhostEntity ghost) {
        if (frameCount == 0) return;

        if (currentFrame >= frameCount) {
            loopDelayCounter++;
            if (loopDelayCounter >= ConfigManager.playbackLoopDelayTicks()) {
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

        ghost.xo = ghost.getX();
        ghost.yo = ghost.getY();
        ghost.zo = ghost.getZ();
        ghost.xOld = ghost.getX();
        ghost.yOld = ghost.getY();
        ghost.zOld = ghost.getZ();
        ghost.setPos(currentX, currentY, currentZ);
        ghost.setDeltaMovement(currentX - previousX, currentY - previousY, currentZ - previousZ);
        ghost.hasImpulse = true;

        float yaw = yawCenti / 100.0f;
        float pitch = pitchCenti / 100.0f;
        ghost.yRotO = ghost.getYRot();
        ghost.xRotO = ghost.getXRot();
        ghost.yHeadRotO = ghost.yHeadRot;
        ghost.yBodyRotO = ghost.yBodyRot;
        ghost.setYRot(yaw);
        ghost.setXRot(pitch);
        ghost.setYHeadRot(yaw);
        ghost.setYBodyRot(yaw);

        ghost.setShiftKeyDown((flags & Frame.FLAG_SNEAK) != 0);
        ghost.setSprinting((flags & Frame.FLAG_SPRINT) != 0);
        ghost.setSwimming((flags & Frame.FLAG_SWIM) != 0);
        ghost.setOnGround((flags & Frame.FLAG_ON_GROUND) != 0);
        ghost.applyRecordedPose(flags);

        if ((flags & Frame.FLAG_ATTACK) != 0 && (previousFlags & Frame.FLAG_ATTACK) == 0) {
            ghost.swing(InteractionHand.MAIN_HAND);
        }
        previousFlags = flags;

        boolean onFire = (flags & Frame.FLAG_ON_FIRE) != 0;
        ghost.setSharedFlagOnFire(onFire);
        ghost.setRemainingFireTicks(onFire ? 20 : 0);
    }

    private void reset(GhostEntity ghost) {
        currentFrame = 0;
        frameTick = 0;
        loopDelayCounter = 0;
        currentX = startX;
        currentY = startY;
        currentZ = startZ;
        frameStartX = startX;
        frameStartY = startY;
        frameStartZ = startZ;
        frameTargetX = startX;
        frameTargetY = startY;
        frameTargetZ = startZ;
        previousFlags = 0;
        ghost.xo = ghost.getX();
        ghost.yo = ghost.getY();
        ghost.zo = ghost.getZ();
        ghost.xOld = ghost.getX();
        ghost.yOld = ghost.getY();
        ghost.zOld = ghost.getZ();
        ghost.setPos(startX, startY, startZ);
        ghost.setDeltaMovement(0.0, 0.0, 0.0);
    }

    /** The oldest retained position, where the ghost begins each playback loop. */
    public double[] getStartPosition() {
        return new double[]{ startX, startY, startZ };
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
            GhostOfYou.LOGGER.warn("Could not compress ghost recording", e);
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
                GhostOfYou.LOGGER.error("Failed to decompress ghost recording", e);
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
