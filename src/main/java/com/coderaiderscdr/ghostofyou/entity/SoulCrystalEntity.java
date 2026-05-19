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

public class SoulCrystalEntity extends Entity {

    @Nullable private UUID linkedGhostUUID;
    private String ownerName = "";

    public SoulCrystalEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();

        this.yRotO = this.getYRot();
        this.setYRot((this.getYRot() + 3f) % 360f);

        if (!level().isClientSide()) {

            if (this.tickCount % 4 == 0 && level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL,
                        getX(), getY() + 1.25, getZ(),
                        2, 0.2, 0.25, 0.2, 0.01);
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        getX(), getY() + 1.25, getZ(),
                        1, 0.15, 0.1, 0.15, 0.01);
            }

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

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof GhostBanisherItem) {
            if (!level().isClientSide() && linkedGhostUUID != null
                    && level() instanceof ServerLevel sl) {
                net.minecraft.world.entity.Entity e = sl.getEntity(linkedGhostUUID);
                if (e instanceof GhostEntity ghost && ghost.isAlive()) {
                    GhostInteraction.startBanishing(player, ghost, hand);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("LinkedGhost")) linkedGhostUUID = tag.getUUID("LinkedGhost");
        ownerName = tag.getString("OwnerName");

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

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

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
