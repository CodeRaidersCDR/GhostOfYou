package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.recording.ActionEvent;
import com.coderaiderscdr.ghostofyou.recording.CircularFrameBuffer;
import com.coderaiderscdr.ghostofyou.recording.Frame;
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
    private static final EntityDataAccessor<Boolean> DATA_RENDER_POS_VALID =
            SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_RENDER_X =
            SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RENDER_Y =
            SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RENDER_Z =
            SynchedEntityData.defineId(GhostEntity.class, EntityDataSerializers.FLOAT);

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

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public GhostEntity(EntityType<? extends GhostEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
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
        this.entityData.define(DATA_RENDER_POS_VALID, false);
        this.entityData.define(DATA_RENDER_X, 0.0f);
        this.entityData.define(DATA_RENDER_Y, 0.0f);
        this.entityData.define(DATA_RENDER_Z, 0.0f);
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
        this.setPlaybackRenderPosition(start[0], start[1], start[2]);
    }

    // ------------------------------------------------------------------
    // Override — suppress AI and physics
    // ------------------------------------------------------------------

    /** Ghost has no AI goals. */
    @Override
    protected void registerGoals() { /* intentionally empty */ }

    /** Ghost has no AI step. HOT PATH avoidance. */
    @Override
    public void aiStep() { /* intentionally empty */ }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);

        if (!level().isClientSide() && !ConfigManager.isPlaybackPaused() && shouldTickPlaybackThisTick()) {
            tickPlayback();
        }
    }

    /**
     * Suppress all LivingEntity physics (gravity, momentum, sliding).
     * Ghost position is set exclusively by {@link PlaybackController#tick(GhostEntity)}.
     * Without this override, LivingEntity.travel() would pull the ghost down with gravity
     * every tick, overriding the setPos() calls from the playback controller.
     */
    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    /** Ghost never takes damage. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) { return true; }

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

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    /**
     * Advance playback by one step.
     * HOT PATH — called with LOD-based frequency from {@code ServerTickHandler}.
     */
    public void tickPlayback() {
        if (playbackController == null) return;
        if (level().isClientSide()) return;

        long gameTick = level().getGameTime();
        if (lastPlaybackGameTick == gameTick) return;
        lastPlaybackGameTick = gameTick;

        playbackController.tick(this);

        // Debug: log position every 100 playback ticks to confirm movement
        int frame = playbackController.getCurrentFrame();
        if (frame % 100 == 0) {
            com.coderaiderscdr.ghostofyou.GhostOfYou.LOGGER.debug(
                    "Ghost[{}] frame={} pos=({},{},{})",
                    getOwnerName(), frame,
                    String.format("%.1f", getX()),
                    String.format("%.1f", getY()),
                    String.format("%.1f", getZ()));
        }
    }

    private boolean shouldTickPlaybackThisTick() {
        long gameTick = level().getGameTime();

        double minDistSq = Double.MAX_VALUE;
        for (Player player : level().players()) {
            double dsq = player.distanceToSqr(this);
            if (dsq < minDistSq) minDistSq = dsq;
        }

        int nearDist = ConfigManager.lodNearDistance();
        int midDist = ConfigManager.lodFarDistance();
        int freezeDist = ConfigManager.lodFreezeDistance();

        double nearSq = (double) nearDist * nearDist;
        double midSq = (double) midDist * midDist;
        double freezeSq = (double) freezeDist * freezeDist;

        if (minDistSq <= nearSq) return true;
        if (minDistSq <= midSq) return gameTick % 4 == 0;
        if (minDistSq <= freezeSq) return gameTick % 10 == 0;
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

    void setPlaybackRenderPosition(double x, double y, double z) {
        this.entityData.set(DATA_RENDER_POS_VALID, true);
        this.entityData.set(DATA_RENDER_X, (float) x);
        this.entityData.set(DATA_RENDER_Y, (float) y);
        this.entityData.set(DATA_RENDER_Z, (float) z);
    }

    public boolean hasPlaybackRenderPosition() {
        return this.entityData.get(DATA_RENDER_POS_VALID);
    }

    public double getPlaybackRenderX() {
        return this.entityData.get(DATA_RENDER_X);
    }

    public double getPlaybackRenderY() {
        return this.entityData.get(DATA_RENDER_Y);
    }

    public double getPlaybackRenderZ() {
        return this.entityData.get(DATA_RENDER_Z);
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

        this.entityData.set(DATA_OWNER_NAME, name);
        this.setCustomName(net.minecraft.network.chat.Component.literal(name));
        this.setCustomNameVisible(true);

        if (tag.contains(NBT_RECORDING)) {
            this.playbackController = PlaybackController.loadFromNbt(
                    tag.getCompound(NBT_RECORDING));
            double[] start = playbackController.getStartPosition();
            this.setPos(start[0], start[1], start[2]);
            this.setPlaybackRenderPosition(start[0], start[1], start[2]);
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
