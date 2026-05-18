package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.recording.ActionEvent;
import com.coderaiderscdr.ghostofyou.recording.CircularFrameBuffer;
import com.coderaiderscdr.ghostofyou.recording.Frame;
import com.coderaiderscdr.ghostofyou.util.ModLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The ghost entity — a translucent, looping playback of a dead player's
 * last N minutes of movement.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Fully invulnerable — {@link #isInvulnerableTo} always returns {@code true}</li>
 *   <li>No physics — {@code noPhysics = true}</li>
 *   <li>No AI — {@link #aiStep()} is intentionally empty</li>
 *   <li>Persistent — {@link #isPersistenceRequired()} returns {@code true}</li>
 *   <li>Playback is visual-only — ghosts NEVER modify the world</li>
 * </ul>
 *
 * Playback is driven from {@link #tick()} on the server side with LOD-aware
 * scheduling.
 */
public class GhostEntity extends Monster {

    // ------------------------------------------------------------------
    // SynchedEntityData keys
    // ------------------------------------------------------------------

    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.STRING);

    // ------------------------------------------------------------------
    // NBT keys
    // ------------------------------------------------------------------

    private static final String NBT_OWNER_UUID    = "OwnerUUID";
    private static final String NBT_OWNER_NAME    = "OwnerName";
    private static final String NBT_DEATH_X       = "DeathX";
    private static final String NBT_DEATH_Y       = "DeathY";
    private static final String NBT_DEATH_Z       = "DeathZ";
    private static final String NBT_CREATION_TIME = "CreationTime";
    private static final String NBT_RECORDING     = "Recording";
    private static final String NBT_DEATH_CAUSE   = "DeathCause";

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    @Nullable private UUID ownerUUID;
    private double deathX, deathY, deathZ;
    private long   creationTime;
    private String deathCauseKey = "unknown";

    /** Set to true when triggerEndOfPlaybackDeath() is called to allow kill() through. */
    private transient boolean dyingState = false;

    @Nullable private PlaybackController playbackController;
    private long lastPlaybackGameTick = Long.MIN_VALUE;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public GhostEntity(EntityType<? extends GhostEntity> type, Level level) {
        super(type, level);
        // NOTE: do NOT set noPhysics = true here. Use noGravity + travel override
        // instead, so that vanilla position sync still works on the client.
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
        this.setNoAi(true);          // disables goal selector AND mob navigation
    }

    // ------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------

    /** Register minimal attributes so LivingEntity machinery is satisfied. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.ARMOR, 0.0);
    }

    // ------------------------------------------------------------------
    // Synched data
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER_NAME, "");
    }

    // ------------------------------------------------------------------
    // Initialisation (called once when spawning a new ghost)
    // ------------------------------------------------------------------

    /**
     * Populate the ghost from a freshly-captured recording.
     * Must be called server-side before the entity is added to the world.
     *
     * @param player   the player who just died
     * @param deathX   X coordinate of death
     * @param deathY   Y coordinate of death
     * @param deathZ   Z coordinate of death
     * @param buffer   the player's frame buffer at the moment of death
     * @param events   the player's action event log at the moment of death
     */
    public void initFromRecording(ServerPlayer player,
                                  double deathX, double deathY, double deathZ,
                                  CircularFrameBuffer buffer,
                                  List<ActionEvent> events) {
        this.ownerUUID    = player.getUUID();
        this.deathX       = deathX;
        this.deathY       = deathY;
        this.deathZ       = deathZ;
        this.creationTime = level().getGameTime();

        String name = player.getName().getString();
        this.entityData.set(DATA_OWNER_NAME, name);
        this.setCustomName(net.minecraft.network.chat.Component.literal(name));
        this.setCustomNameVisible(true);

        byte[] rawFrames = buffer.toByteArray();
        this.playbackController = new PlaybackController(rawFrames, deathX, deathY, deathZ);

        double[] start = playbackController.getStartPosition();
        this.setPos(start[0], start[1], start[2]);
    }

    // ------------------------------------------------------------------
    // Override — suppress AI and physics
    // ------------------------------------------------------------------

    /** Ghost has no AI goals. */
    @Override
    protected void registerGoals() { /* intentionally empty */ }

    /**
     * IMPORTANT: Do NOT override aiStep() with empty body.
     * Vanilla Mob.aiStep() handles client-side lerpSteps interpolation —
     * removing it makes the ghost ignore server position packets.
     *
     * Goals are not registered, and setNoAi(true) is set in constructor,
     * so no AI logic actually runs.
     */

    @Override
    public void tick() {
        super.tick();

        // Block gravity every tick (in case external code re-enables it)
        this.setNoGravity(true);

        // Playback is driven externally by ServerTickHandler.tickPlayback(stride).
        // Walk animation is updated here so it stays current every render tick.
        Vec3 delta = this.getDeltaMovement();
        float horizontalSpeed = (float) Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float animSpeed = Math.min(1.0f, horizontalSpeed * 4.0f);
        this.walkAnimation.update(animSpeed, 0.4f);
    }

    /**
     * Block vanilla physics (gravity, momentum). Position is set exclusively
     * by PlaybackController via setPos(). Without this override,
     * LivingEntity.travel() would apply gravity each tick.
     */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isAlive()) {
            // Intentionally no movement application. Keep delta movement as set
            // by playback controller (used for walk animation speed).
            this.calculateEntityAnimation(false);
        }
        // During death animation let vanilla handle position (entity tips over in place).
    }

    /** Ghost never takes damage — unless {@link #triggerEndOfPlaybackDeath()} was called. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) { return !dyingState; }

    /** Ghost cannot be leashed. */
    @Override
    public boolean canBeLeashed(Player player) { return false; }

    /** Ghost does not push or get pushed. */
    @Override
    public boolean isPushable() { return false; }

    /** Ghost never despawns naturally. */
    @Override
    public boolean isPersistenceRequired() { return true; }

    @Override
    public boolean isPickable() { return true; }

    /** Ghost drops nothing directly through normal loot (banisher awards essence manually). */
    @Override
    protected void dropAllDeathLoot(DamageSource damageSource) { /* no-op */ }

    /**
     * Called by {@link PlaybackController} when the last recorded frame has been
     * played back (the ghost has "re-lived" its death).
     * Bypasses {@link #isInvulnerableTo} and triggers the vanilla death animation,
     * after which the entity is automatically removed.
     */
    void triggerEndOfPlaybackDeath() {
        if (!this.isAlive()) return;
        this.dyingState = true;
        this.kill(); // hurt(genericKill, MAX_FLOAT) → die() → 20-tick death anim
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    /**
     * Advance playback by {@code stride} virtual game ticks.
     * Called by {@link com.coderaiderscdr.ghostofyou.event.ServerTickHandler},
     * which handles the LOD-based scheduling (near zone stride=1,
     * mid zone stride=4, etc.).
     *
     * <p>Advancing by multiple virtual ticks at once compensates for skipped
     * server ticks so the ghost stays temporally in sync with the recording.
     *
     * @param stride number of game-tick steps to advance (≥ 1)
     */
    public void tickPlayback(int stride) {
        if (playbackController == null) return;
        if (level().isClientSide()) return;
        if (!this.isAlive()) return;

        long gameTick = level().getGameTime();
        if (lastPlaybackGameTick == gameTick) return;
        lastPlaybackGameTick = gameTick;

        for (int i = 0; i < stride && this.isAlive(); i++) {
            playbackController.tick(this);
        }

        int frame = playbackController.getCurrentFrame();
        if (ModLogger.PLAYBACK.isDebugEnabled() && frame % 100 == 0) {
            ModLogger.PLAYBACK.debug("Ghost[{}] frame={} pos=({},{},{}) delta=({},{},{})",
                    getOwnerName(), frame,
                    String.format("%.2f", getX()),
                    String.format("%.2f", getY()),
                    String.format("%.2f", getZ()),
                    String.format("%.3f", getDeltaMovement().x),
                    String.format("%.3f", getDeltaMovement().y),
                    String.format("%.3f", getDeltaMovement().z));
        }
    }

    void applyRecordedPose(byte flags) {
        if ((flags & Frame.FLAG_SWIM) != 0) {
            this.setPose(Pose.SWIMMING);
        } else if ((flags & Frame.FLAG_FLY) != 0) {
            this.setPose(Pose.FALL_FLYING);
        } else if ((flags & Frame.FLAG_SNEAK) != 0) {
            this.setPose(Pose.CROUCHING);
        } else {
            this.setPose(Pose.STANDING);
        }
    }

    // ------------------------------------------------------------------
    // NBT persistence
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID(NBT_OWNER_UUID, ownerUUID);
        tag.putString(NBT_OWNER_NAME,    entityData.get(DATA_OWNER_NAME));
        tag.putDouble(NBT_DEATH_X,       deathX);
        tag.putDouble(NBT_DEATH_Y,       deathY);
        tag.putDouble(NBT_DEATH_Z,       deathZ);
        tag.putLong  (NBT_CREATION_TIME, creationTime);
        if (playbackController != null) {
            tag.put(NBT_RECORDING, playbackController.saveToNbt(deathX, deathY, deathZ));
        }
        tag.putString(NBT_DEATH_CAUSE, deathCauseKey);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(NBT_OWNER_UUID)) ownerUUID = tag.getUUID(NBT_OWNER_UUID);
        String name = tag.getString(NBT_OWNER_NAME);
        deathX       = tag.getDouble(NBT_DEATH_X);
        deathY       = tag.getDouble(NBT_DEATH_Y);
        deathZ       = tag.getDouble(NBT_DEATH_Z);
        creationTime = tag.getLong(NBT_CREATION_TIME);

        this.entityData.set(DATA_OWNER_NAME, name);
        this.setCustomName(net.minecraft.network.chat.Component.literal(name));
        this.setCustomNameVisible(true);

        if (tag.contains(NBT_RECORDING)) {
            this.playbackController = PlaybackController.loadFromNbt(
                    tag.getCompound(NBT_RECORDING));
            double[] start = playbackController.getStartPosition();
            this.setPos(start[0], start[1], start[2]);
        }
        if (tag.contains(NBT_DEATH_CAUSE)) {
            this.deathCauseKey = tag.getString(NBT_DEATH_CAUSE);
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
                                                  DifficultyInstance difficulty,
                                                  MobSpawnType reason,
                                                  @Nullable SpawnGroupData spawnData,
                                                  @Nullable CompoundTag dataTag) {
        return spawnData; // skip default spawning behaviour
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    @Nullable public UUID getOwnerUUID() { return ownerUUID; }

    public String getOwnerName() { return entityData.get(DATA_OWNER_NAME); }

    public double getDeathX() { return deathX; }
    public double getDeathY() { return deathY; }
    public double getDeathZ() { return deathZ; }

    public long getCreationTime() { return creationTime; }

    /** Translation key for the cause of this ghost's original death (e.g. {@code "death.attack.fall"}). */
    public String getDeathCauseKey() { return deathCauseKey; }
    public void   setDeathCauseKey(String key) { this.deathCauseKey = key; }

    @Nullable public PlaybackController getPlaybackController() { return playbackController; }

    // ------------------------------------------------------------------
    // Interaction — handled by GhostInteraction
    // ------------------------------------------------------------------

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player,
                                                                net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof com.coderaiderscdr.ghostofyou.item.GhostBanisherItem) {
            if (!level().isClientSide()) {
                GhostInteraction.startBanishing(player, this, hand);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide());
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------------
    // Glowing (based on client config, falls back to server tag)
    // ------------------------------------------------------------------

    @Override
    public boolean isCurrentlyGlowing() {
        return super.isCurrentlyGlowing();
    }
}
