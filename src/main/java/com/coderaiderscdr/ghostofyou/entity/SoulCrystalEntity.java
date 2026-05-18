package com.coderaiderscdr.ghostofyou.entity;

import com.coderaiderscdr.ghostofyou.item.GhostBanisherItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A stationary glowing orb that marks a player's death location.
 *
 * <p>Spawned alongside a {@link GhostEntity} when a player dies. The orb:
 * <ul>
 *   <li>Hovers and rotates at the death coordinates</li>
 *   <li>Emits soul-fire particles so it is always visible</li>
 *   <li>Can be right-clicked with a Ghost Banisher to begin the banishing ritual</li>
 *   <li>Auto-despawns when its linked GhostEntity is gone</li>
 * </ul>
 */
public class SoulCrystalEntity extends Entity {

    @Nullable private UUID linkedGhostUUID;
    private String ownerName = "";

    public SoulCrystalEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        // Slow clockwise spin
        this.yRotO = this.getYRot();
        this.setYRot((this.getYRot() + 3f) % 360f);

        if (!level().isClientSide()) {
            // Soul fire particle halo
            if (this.tickCount % 4 == 0 && level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL,
                        getX(), getY() + 1.25, getZ(),
                        2, 0.2, 0.25, 0.2, 0.01);
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        getX(), getY() + 1.25, getZ(),
                        1, 0.15, 0.1, 0.15, 0.01);
            }

            // After a brief startup delay, check if the linked ghost still exists.
            // Delay avoids false-positive despawn on world load before chunks load.
            if (this.tickCount > 100 && this.tickCount % 40 == 0 && linkedGhostUUID != null) {
                if (level() instanceof ServerLevel sl) {
                    net.minecraft.world.entity.Entity ghost = sl.getEntity(linkedGhostUUID);
                    if (ghost == null || !ghost.isAlive()) {
                        this.discard();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Interaction — Ghost Banisher right-click
    // ------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide() && linkedGhostUUID != null) {
            if (player.getItemInHand(hand).getItem() instanceof GhostBanisherItem) {
                if (level() instanceof ServerLevel sl) {
                    net.minecraft.world.entity.Entity e = sl.getEntity(linkedGhostUUID);
                    if (e instanceof GhostEntity ghost && ghost.isAlive()) {
                        GhostInteraction.startBanishing(player, ghost, hand);
                        return InteractionResult.sidedSuccess(false);
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------
    // Entity basics
    // ------------------------------------------------------------------

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("LinkedGhost")) linkedGhostUUID = tag.getUUID("LinkedGhost");
        ownerName = tag.getString("OwnerName");
        // Restore custom name tag so the label survives restarts
        if (!ownerName.isEmpty()) {
            this.setCustomName(net.minecraft.network.chat.Component.literal(ownerName));
            this.setCustomNameVisible(true);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (linkedGhostUUID != null) tag.putUUID("LinkedGhost", linkedGhostUUID);
        tag.putString("OwnerName", ownerName);
    }

    // ------------------------------------------------------------------
    // Networking (Forge)
    // ------------------------------------------------------------------

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public void setLinkedGhostUUID(UUID uuid) { this.linkedGhostUUID = uuid; }

    public void setOwnerName(String name) {
        this.ownerName = name;
        this.setCustomName(net.minecraft.network.chat.Component.literal(name));
        this.setCustomNameVisible(true);
    }

    @Nullable
    public UUID getLinkedGhostUUID() { return linkedGhostUUID; }

    public String getCrystalOwnerName() { return ownerName; }
}
