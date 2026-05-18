package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.recording.ActionEvent;
import com.coderaiderscdr.ghostofyou.recording.CircularFrameBuffer;
import com.coderaiderscdr.ghostofyou.recording.Frame;
import com.coderaiderscdr.ghostofyou.util.ModLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    @Nullable private UUID ownerUUID;
    private double deathX, deathY, deathZ;
    private long   creationTime;

    @Nullable private PlaybackController playbackController;
    private long lastPlaybackGameTick = Long.MIN_VALUE;

    /** Owner name to apply on {@link #onAddedToWorld()} (avoids early synced-data flush). */
    @Nullable private String pendingOwnerName;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public GhostEntity(EntityType<? extends GhostEntity> type, Level level) {
        super(type, level);
        // noPhysics = true: ghost passes through blocks.
        // This is safe because travel() is overridden as no-op (so Entity.move() is never
        // called), and moveTo() in PlaybackController sets position directly via packet path.
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
        this.setNoAi(true);          // disables goal selector AND mob navigation
        ModLogger.SPAWN.debug("GhostEntity constructed (level={})", level.getClass().getSimpleName());
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

        this.pendingOwnerName = player.getName().getString();

        byte[] rawFrames = buffer.toByteArray();
        this.playbackController = new PlaybackController(rawFrames, deathX, deathY, deathZ);

        double[] start = playbackController.getStartPosition();
        this.moveTo(start[0], start[1], start[2], player.getYRot(), player.getXRot());

        ModLogger.SPAWN.info("Ghost of {} spawning: start=({}, {}, {}) -> death=({}, {}, {}), {} frames",
                pendingOwnerName, start[0], start[1], start[2], deathX, deathY, deathZ,
                playbackController.getFrameCount());
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        // Now that we're tracked by the chunk entity manager, it's safe to flush synced data.
        if (pendingOwnerName != null) {
            this.entityData.set(DATA_OWNER_NAME, pendingOwnerName);
            this.setCustomName(Component.literal(pendingOwnerName));
            this.setCustomNameVisible(true);
            ModLogger.SPAWN.info("Ghost[{}] added to world at ({}, {}, {}) frames={}",
                    pendingOwnerName, getX(), getY(), getZ(),
                    playbackController != null ? playbackController.getFrameCount() : 0);
            pendingOwnerName = null;
        } else {
            ModLogger.SPAWN.debug("Ghost[{}] re-added to world (loaded from NBT) at ({}, {}, {})",
                    getOwnerName(), getX(), getY(), getZ());
        }
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
        this.setNoGravity(true);

        if (!level().isClientSide()
                && !ConfigManager.isPlaybackPaused()
                && playbackController != null
                && shouldTickPlaybackThisTick()) {
            tickPlayback();
        } else if (!level().isClientSide() && playbackController == null) {
            // Warn once every 200 ticks if ghost has no recording
            if (tickCount % 200 == 0) {
                ModLogger.PLAYBACK.warn("Ghost[{}] has no PlaybackController — stuck! (tick={})",
                        getOwnerName(), tickCount);
            }
        }

        // Walk animation driven by horizontal delta (auto-synced via LivingEntity).
        Vec3 d = this.getDeltaMovement();
        float speed = (float) Math.sqrt(d.x * d.x + d.z * d.z) * 4.0f;
        if (speed > 1.0f) speed = 1.0f;
        this.walkAnimation.update(speed, 0.4f);
    }

    /**
     * Blocks vanilla physics. Position is set exclusively by PlaybackController.
     * walkAnimation already updated in tick() so don't recompute here.
     */
    @Override
    public void travel(Vec3 travelVector) {
        // no-op: do not apply gravity, no movement integration
    }

    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }

    /** Ghost cannot be leashed. */
    @Override
    public boolean canBeLeashed(Player player) { return false; }

    /** Ghost does not push or get pushed. */
    @Override
    public boolean isPushable() { return false; }

    /** Ghost never despawns naturally. */
    @Override
    public boolean isPersistenceRequired() { return true; }

    @Override public boolean isPickable() { return true; }
    @Override protected void dropAllDeathLoot(DamageSource damageSource) { /* no-op */ }

    public void tickPlayback() {
        if (playbackController == null || level().isClientSide()) return;

        long gameTick = level().getGameTime();
        if (lastPlaybackGameTick == gameTick) return;
        lastPlaybackGameTick = gameTick;

        try {
            playbackController.tick(this);
        } catch (Exception e) {
            ModLogger.PLAYBACK.error("Ghost[{}] EXCEPTION during playback tick at frame={}: {}",
                    getOwnerName(), playbackController.getCurrentFrame(), e.getMessage(), e);
        }

        if (ModLogger.PLAYBACK.isDebugEnabled()
                && playbackController.getCurrentFrame() % 20 == 0) {
            ModLogger.PLAYBACK.debug("Ghost[{}] frame={}/{} pos=({}, {}, {}) delta=({}, {}, {}) alive={}",
                    getOwnerName(),
                    playbackController.getCurrentFrame(),
                    playbackController.getFrameCount(),
                    getX(), getY(), getZ(),
                    getDeltaMovement().x, getDeltaMovement().y, getDeltaMovement().z,
                    isAlive());
        }
    }

    private boolean shouldTickPlaybackThisTick() {
        long gameTick = level().getGameTime();
        double minDistSq = Double.MAX_VALUE;
        for (Player p : level().players()) {
            double dsq = p.distanceToSqr(this);
            if (dsq < minDistSq) minDistSq = dsq;
        }
        int n  = ConfigManager.lodNearDistance();
        int m  = ConfigManager.lodFarDistance();
        int f  = ConfigManager.lodFreezeDistance();
        double nSq = (double) n * n, mSq = (double) m * m, fSq = (double) f * f;

        if (minDistSq <= nSq) return true;
        if (minDistSq <= mSq) return gameTick % 4 == 0;
        if (minDistSq <= fSq) return gameTick % 10 == 0;
        return gameTick % 20 == 0;
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

        // Safe here because reading happens during chunk load and the entity
        // is added to tracking right after; still defer via pendingOwnerName.
        this.pendingOwnerName = name;

        if (tag.contains(NBT_RECORDING)) {
            try {
                this.playbackController = PlaybackController.loadFromNbt(
                        tag.getCompound(NBT_RECORDING));
                double[] start = playbackController.getStartPosition();
                this.moveTo(start[0], start[1], start[2], this.getYRot(), this.getXRot());
                ModLogger.SPAWN.info("Ghost[{}] loaded from NBT — death=({}, {}, {}) frames={}",
                        name, deathX, deathY, deathZ, playbackController.getFrameCount());
            } catch (Exception e) {
                ModLogger.SPAWN.error("Ghost[{}] FAILED to load PlaybackController from NBT: {}",
                        name, e.getMessage(), e);
            }
        } else {
            ModLogger.SPAWN.warn("Ghost[{}] NBT has no recording data — ghost will be stuck!", name);
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
    public String getOwnerName() {
        String synced = entityData.get(DATA_OWNER_NAME);
        return synced.isEmpty() && pendingOwnerName != null ? pendingOwnerName : synced;
    }
    public double getDeathX() { return deathX; }
    public double getDeathY() { return deathY; }
    public double getDeathZ() { return deathZ; }
    public long getCreationTime() { return creationTime; }
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

}
