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

 

public class PlaybackController {

     
    private final ByteBuffer frames;
    private final int frameCount;

     
    private final double startX, startY, startZ;

     

    private final int    loopStartFrame;
    private final double loopStartX, loopStartY, loopStartZ;

     
    private double currentX, currentY, currentZ;

     
    private int currentFrame;
    private int frameTick;
    private double frameStartX, frameStartY, frameStartZ;
    private double frameTargetX, frameTargetY, frameTargetZ;

     
    private boolean playbackComplete = false;

     

    public PlaybackController(byte[] rawFrameBytes, double deathX, double deathY, double deathZ) {
        
        final int MIN_TICKS = 60;           
        final int MAX_TICKS = 200;          
        final double MIN_PATH_BLOCKS = 0.5; 
        int incomingFrames = rawFrameBytes.length / Frame.BYTES;
        if (incomingFrames > 1) {
            java.nio.ByteBuffer scan = java.nio.ByteBuffer.wrap(rawFrameBytes);
            int tickAcc = 0; double pathLength = 0.0; int firstFrame = incomingFrames;
            while (firstFrame > 0) {
                firstFrame--;
                tickAcc += Math.max(1, (int) Frame.readTickDelta(scan, firstFrame));
                float fdx = Frame.readDeltaX(scan, firstFrame);
                float fdy = Frame.readDeltaY(scan, firstFrame);
                float fdz = Frame.readDeltaZ(scan, firstFrame);
                pathLength += Math.sqrt((double)fdx*fdx + (double)fdy*fdy + (double)fdz*fdz);
                if (tickAcc >= MIN_TICKS && pathLength >= MIN_PATH_BLOCKS) break;
                if (tickAcc >= MAX_TICKS) break;
            }
            if (firstFrame > 0) {
                int keptFrames = incomingFrames - firstFrame;
                byte[] trimmed = new byte[keptFrames * Frame.BYTES];
                System.arraycopy(rawFrameBytes, firstFrame * Frame.BYTES, trimmed, 0, trimmed.length);
                ModLogger.PLAYBACK.debug("Trimmed recording: {} -> {} frames ({} ticks, path={} blocks)",
                        incomingFrames, keptFrames, tickAcc, Math.round(pathLength * 10.0) / 10.0);
                rawFrameBytes = trimmed;
            } else {
                ModLogger.PLAYBACK.debug("Using full recording: {} frames ({} ticks, path={} blocks)",
                        incomingFrames, tickAcc, Math.round(pathLength * 10.0) / 10.0);
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

     

    public void tick(GhostEntity ghost) {
        if (frameCount == 0) return;

        if (currentFrame >= frameCount) {
            
            if (!playbackComplete) {
                playbackComplete = true;
                ModLogger.PLAYBACK.info("Ghost[{}] reached end of recording ({} frames). Playing loop death animation.",
                        ghost.getOwnerName(), frameCount);
                ghost.startLoopDeathAnimation();
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

        boolean onFire = (flags & Frame.FLAG_ON_FIRE) != 0;
        ghost.setSharedFlagOnFire(onFire);
        ghost.setRemainingFireTicks(onFire ? 20 : 0);
    }

    void reset(GhostEntity ghost) {
        currentFrame = loopStartFrame;
        frameTick = 0;
        playbackComplete = false;
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

     
    public double[] getStartPosition() {
        return new double[]{ loopStartX, loopStartY, loopStartZ };
    }

    public int getFrameCount() { return frameCount; }

    public int getCurrentFrame() { return currentFrame; }

    private static double lerp(double start, double end, double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return start + (end - start) * clamped;
    }

     

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

     
    public int frameBytesSize() {
        return frameCount * Frame.BYTES;
    }

     
    public float kilobytes() {
        return frameCount * Frame.BYTES / 1024f;
    }
}

