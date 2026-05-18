package com.coderaiderscdr.ghostofyou.recording;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.util.ModLogger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player recorder. One instance is created when a player logs in and
 * destroyed when the player logs out.
 *
 * <p>The recorder maintains a {@link CircularFrameBuffer} (off-heap) and an
 * {@link ActionEventLog}. It is ticked by {@code PlayerTickHandler}.
 */
public class PlayerRecorder {

    private final ServerPlayer player;
    private CircularFrameBuffer frameBuffer;
    private ActionEventLog eventLog;

    // Previous-frame state (for delta encoding)
    private double prevX, prevY, prevZ;
    private float  prevYaw, prevPitch;
    private int    prevHeldSlot;

    // Last position known to be "safe" (on ground / in water)
    private double lastSafeX, lastSafeY, lastSafeZ;

    private int tickSinceLastFrame = 0;
    private boolean initialized = false;

    public PlayerRecorder(ServerPlayer player) {
        this.player = player;
        rebuildBuffer();
    }

    // ------------------------------------------------------------------
    // Buffer lifecycle
    // ------------------------------------------------------------------

    /** Rebuild the buffer according to current config values. */
    public void rebuildBuffer() {
        int durationSec    = ConfigManager.recordingDurationSeconds();
        int sampleInterval = ConfigManager.sampleIntervalTicks();
        int capacity       = Math.max(1, durationSec * 20 / sampleInterval);
        this.frameBuffer   = new CircularFrameBuffer(capacity);
        this.eventLog      = new ActionEventLog();
        this.initialized   = false;          // forces re-init on next captureFrame
        this.tickSinceLastFrame = 0;
        // Explicitly reset delta-state to current player position so the first
        // frame after respawn doesn't accumulate a stale dead-position delta.
        this.prevX    = player.getX();
        this.prevY    = player.getY();
        this.prevZ    = player.getZ();
        this.prevYaw   = player.getYRot();
        this.prevPitch = player.getXRot();
        this.lastSafeX = player.getX();
        this.lastSafeY = player.getY();
        this.lastSafeZ = player.getZ();
        ModLogger.RECORDING.debug("Rebuilt buffer for {} cap={}", player.getName().getString(), capacity);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /**
     * Called every server tick by {@code PlayerTickHandler}.
     * HOT PATH — samples one frame every {@code sampleIntervalTicks} ticks.
     */
    public void tick() {
        if (!ConfigManager.isRecordingEnabled()) return;

        int interval = ConfigManager.sampleIntervalTicks();
        if (++tickSinceLastFrame < interval) return;
        int ticksSincePreviousFrame = tickSinceLastFrame;
        tickSinceLastFrame = 0;

        captureFrame(ticksSincePreviousFrame);
    }

    /**
     * Capture the current state immediately, regardless of the configured sample
     * interval. Used at death so the recording ends at the actual death pose.
     */
    public void captureImmediateFrame() {
        int ticksSincePreviousFrame = Math.max(1, tickSinceLastFrame);
        tickSinceLastFrame = 0;
        captureFrame(ticksSincePreviousFrame);
    }

    public void captureEmergencyDeathFrames(double x, double y, double z) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        short yawCenti   = (short) Math.round(yaw   * 100f);
        short pitchCenti = (short) Math.round(pitch * 100f);
        byte flags = Frame.FLAG_ON_GROUND;

        // Pretend the player walked 1 block forward then stopped — gives some
        // visible movement in the playback so it's not a literal corpse.
        float dirX = -(float) Math.sin(Math.toRadians(yaw)) * 0.5f;
        float dirZ =  (float) Math.cos(Math.toRadians(yaw)) * 0.5f;

        frameBuffer.write((short) 20, dirX, dirZ, 0f,
                yawCenti, pitchCenti, flags, (byte) 0, 0, 0);
        frameBuffer.write((short) 20, 0f, 0f, 0f,
                yawCenti, pitchCenti, flags, (byte) 0, 0, 0);

        ModLogger.RECORDING.info("Captured emergency death frames for {} at ({},{},{})",
                player.getName().getString(), x, y, z);
    }

    /** Sample the player's current state and write one frame. */
    private void captureFrame(int ticksSincePreviousFrame) { // HOT PATH
        double x   = player.getX();
        double y   = player.getY();
        double z   = player.getZ();
        float  yaw   = player.getYRot();
        float  pitch = player.getXRot();
        int    slot  = player.getInventory().selected;

        if (!initialized) {
            prevX = x; prevY = y; prevZ = z;
            prevYaw = yaw; prevPitch = pitch;
            prevHeldSlot = slot;
            lastSafeX = x; lastSafeY = y; lastSafeZ = z;
            initialized = true;
        }

        float dx = (float) (x - prevX);
        float dy = (float) (y - prevY);
        float dz = (float) (z - prevZ);

        short yawCenti   = (short) Math.round(yaw   * 100f);
        short pitchCenti = (short) Math.round(pitch * 100f);
        short tickDelta  = (short) Math.max(1, Math.min(Short.MAX_VALUE, ticksSincePreviousFrame));

        byte flags = 0;
        if (player.isShiftKeyDown())            flags |= Frame.FLAG_SNEAK;
        if (player.isSprinting())               flags |= Frame.FLAG_SPRINT;
        if (player.isSwimming())                flags |= Frame.FLAG_SWIM;
        if (player.getAbilities().flying)       flags |= Frame.FLAG_FLY;
        if (player.onGround())                  flags |= Frame.FLAG_ON_GROUND;
        if (player.isUsingItem())               flags |= Frame.FLAG_USING_ITEM;
        if (player.isOnFire())                  flags |= Frame.FLAG_ON_FIRE;

        frameBuffer.write(tickDelta, dx, dz, dy,
                yawCenti, pitchCenti, flags,
                (byte) slot, 0, 0);

        // Track last safe position
        if (player.onGround() || player.isInWater()) {
            lastSafeX = x; lastSafeY = y; lastSafeZ = z;
        }

        prevX = x; prevY = y; prevZ = z;
        prevYaw = yaw; prevPitch = pitch;
        prevHeldSlot = slot;
    }

    // ------------------------------------------------------------------
    // Action event recording
    // ------------------------------------------------------------------

    /**
     * Record an action event that occurred this tick.
     * The event is appended to the log; its ID can optionally be patched into the last frame.
     *
     * @param event the action event to record
     */
    public void recordActionEvent(ActionEvent event) {
        if (!ConfigManager.isRecordingEnabled()) return;
        eventLog.append(event);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public CircularFrameBuffer getFrameBuffer() { return frameBuffer; }
    public ActionEventLog      getEventLog()    { return eventLog; }
    public ServerPlayer        getPlayer()      { return player; }

    /** Last absolute position known to be safe (on ground or in water). */
    public double[] getLastSafePosition() {
        return new double[]{ lastSafeX, lastSafeY, lastSafeZ };
    }
}
