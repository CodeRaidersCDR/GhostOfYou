package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.recording.Frame;
import com.coderaiderscdr.ghostofyou.util.ModLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class PlaybackController {

    private final ByteBuffer frames;
    private final int frameCount;

    private final double startX, startY, startZ;

    private double currentX, currentY, currentZ;
    private int currentFrame;
    private int frameTick;
    private double frameStartX, frameStartY, frameStartZ;
    private double frameTargetX, frameTargetY, frameTargetZ;

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
        // --- Trim to the last 3 seconds (60 ticks) with meaningful movement ---
        // Walk backwards through frames accumulating ticks AND total path length.
        // We keep extending the window past 3s if the player was standing still,
        // until we find at least MIN_PATH_BLOCKS of movement OR hit MAX_TICKS.
        // This guarantees the ghost always shows actual movement, not a frozen corpse.
        final int MIN_TICKS = 60;         // 3 seconds minimum
        final int MAX_TICKS = 600;        // 30 seconds maximum cap
        final double MIN_PATH_BLOCKS = 0.5; // minimum total path length to count as "moving"

        int incomingFrames = rawFrameBytes.length / Frame.BYTES;
        if (incomingFrames > 1) {
            ByteBuffer scan = ByteBuffer.wrap(rawFrameBytes);
            int tickAcc = 0;
            double pathLength = 0.0;
            int firstFrame = incomingFrames;

            while (firstFrame > 0) {
                firstFrame--;
                tickAcc += Math.max(1, (int) Frame.readTickDelta(scan, firstFrame));
                float fdx = Frame.readDeltaX(scan, firstFrame);
                float fdy = Frame.readDeltaY(scan, firstFrame);
                float fdz = Frame.readDeltaZ(scan, firstFrame);
                pathLength += Math.sqrt((double) fdx * fdx + (double) fdy * fdy + (double) fdz * fdz);

                // Stop as soon as we have both: enough time AND enough movement
                if (tickAcc >= MIN_TICKS && pathLength >= MIN_PATH_BLOCKS) break;
                // Hard cap — don't pull in more than 30s regardless
                if (tickAcc >= MAX_TICKS) break;
            }

            if (firstFrame > 0) {
                int keptFrames = incomingFrames - firstFrame;
                byte[] trimmed = new byte[keptFrames * Frame.BYTES];
                System.arraycopy(rawFrameBytes, firstFrame * Frame.BYTES, trimmed, 0, trimmed.length);
                ModLogger.PLAYBACK.debug("Trimmed recording: {} -> {} frames ({} ticks ≈ {}s, path={} blocks)",
                        incomingFrames, keptFrames, tickAcc, tickAcc / 20,
                        Math.round(pathLength * 10.0) / 10.0);
                rawFrameBytes = trimmed;
            } else {
                ModLogger.PLAYBACK.debug("Using full recording: {} frames ({} ticks ≈ {}s, path={} blocks)",
                        incomingFrames, tickAcc, tickAcc / 20,
                        Math.round(pathLength * 10.0) / 10.0);
            }
        }

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
        this.frameStartX  = startX; this.frameStartY  = startY; this.frameStartZ  = startZ;
        this.frameTargetX = startX; this.frameTargetY = startY; this.frameTargetZ = startZ;

        ModLogger.PLAYBACK.info("PlaybackController created: frames={} startPos=({}, {}, {}) death=({}, {}, {})",
                frameCount, startX, startY, startZ, deathX, deathY, deathZ);
    }

    public void tick(GhostEntity ghost) {
        if (frameCount == 0) return;

        // End of recording — wait the configured delay then loop. NEVER kill the ghost.
        if (currentFrame >= frameCount) {
            loopDelayCounter++;
            if (loopDelayCounter == 1) {
                ModLogger.PLAYBACK.info("Ghost[{}] reached end of recording ({} frames). Waiting {} ticks before loop.",
                        ghost.getOwnerName(), frameCount, ConfigManager.playbackLoopDelayTicks());
            }
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

        float yaw   = yawCenti   / 100.0f;
        float pitch = pitchCenti / 100.0f;

        // CRITICAL: do NOT touch xOld/yOld/zOld or yRotO/xRotO here.
        // super.tick() (Entity.baseTick → setOldPosAndRot) already captured them
        // BEFORE this method runs.

        // moveTo properly registers the move for packet generation.
        ghost.moveTo(currentX, currentY, currentZ, yaw, pitch);
        ghost.setYHeadRot(yaw);
        ghost.setYBodyRot(yaw);

        ghost.setDeltaMovement(
                currentX - previousX,
                currentY - previousY,
                currentZ - previousZ);

        ghost.setShiftKeyDown((flags & Frame.FLAG_SNEAK) != 0);
        ghost.setSprinting((flags & Frame.FLAG_SPRINT) != 0);
        ghost.setSwimming((flags & Frame.FLAG_SWIM) != 0);
        ghost.setOnGround((flags & Frame.FLAG_ON_GROUND) != 0);
        ghost.applyRecordedPose(flags);

        // Log significant flag state changes
        if ((flags & Frame.FLAG_SNEAK) != (previousFlags & Frame.FLAG_SNEAK))
            ModLogger.PLAYBACK.debug("Ghost[{}] SNEAK {}", ghost.getOwnerName(), (flags & Frame.FLAG_SNEAK) != 0 ? "ON" : "OFF");
        if ((flags & Frame.FLAG_SPRINT) != (previousFlags & Frame.FLAG_SPRINT))
            ModLogger.PLAYBACK.debug("Ghost[{}] SPRINT {}", ghost.getOwnerName(), (flags & Frame.FLAG_SPRINT) != 0 ? "ON" : "OFF");
        if ((flags & Frame.FLAG_SWIM) != (previousFlags & Frame.FLAG_SWIM))
            ModLogger.PLAYBACK.debug("Ghost[{}] SWIM {}", ghost.getOwnerName(), (flags & Frame.FLAG_SWIM) != 0 ? "ON" : "OFF");
        if ((flags & Frame.FLAG_FLY) != (previousFlags & Frame.FLAG_FLY))
            ModLogger.PLAYBACK.debug("Ghost[{}] FLY {}", ghost.getOwnerName(), (flags & Frame.FLAG_FLY) != 0 ? "ON" : "OFF");
        if ((flags & Frame.FLAG_ON_FIRE) != (previousFlags & Frame.FLAG_ON_FIRE))
            ModLogger.PLAYBACK.info("Ghost[{}] FIRE {}", ghost.getOwnerName(), (flags & Frame.FLAG_ON_FIRE) != 0 ? "ON" : "OFF");

        if ((flags & Frame.FLAG_ATTACK) != 0 && (previousFlags & Frame.FLAG_ATTACK) == 0) {
            ghost.swing(InteractionHand.MAIN_HAND);
            ModLogger.PLAYBACK.debug("Ghost[{}] ATTACK swing at frame={}", ghost.getOwnerName(), currentFrame);
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
        currentX = startX; currentY = startY; currentZ = startZ;
        frameStartX  = startX; frameStartY  = startY; frameStartZ  = startZ;
        frameTargetX = startX; frameTargetY = startY; frameTargetZ = startZ;
        previousFlags = 0;
        ghost.moveTo(startX, startY, startZ, ghost.getYRot(), ghost.getXRot());
        ghost.setDeltaMovement(0.0, 0.0, 0.0);
        ModLogger.PLAYBACK.info("Ghost[{}] LOOP RESET — restarting from ({}, {}, {})",
                ghost.getOwnerName(), startX, startY, startZ);
    }

    public double[] getStartPosition() { return new double[]{ startX, startY, startZ }; }

    public int getFrameCount()   { return frameCount; }
    public int getCurrentFrame() { return currentFrame; }

    private static double lerp(double a, double b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return a + (b - a) * clamped;
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

        byte[] raw = new byte[frameCount * Frame.BYTES];
        ByteBuffer view = frames.duplicate();
        view.position(0).limit(raw.length);
        view.get(raw);

        byte[] stored;
        boolean compressed = false;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(raw.length / 3 + 64);
            try (GZIPOutputStream gz = new GZIPOutputStream(baos)) { gz.write(raw); }
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

    public int frameBytesSize() { return frameCount * Frame.BYTES; }
    public float kilobytes()    { return frameCount * Frame.BYTES / 1024f; }
}
