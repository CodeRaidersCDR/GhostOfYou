package com.coderaiderscdr.ghostofyou.block.entity;

import com.coderaiderscdr.ghostofyou.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class MemorialBlockEntity extends BlockEntity {

    private static final String NBT_BOUND_OWNER       = "BoundOwner";
    private static final String NBT_BANISHED_AT        = "BanishedAt";
    private static final String NBT_OWNER_LIFE_MINUTES = "OwnerLifeMinutes";
    private static final String NBT_DEATH_CAUSE        = "DeathCause";
    private static final String NBT_KILLER_NAME        = "KillerName";
    private static final String NBT_RECORDING          = "Recording";

    private String    boundOwner       = "";
    private long      banishedAt       = 0L;
    private long      ownerLifeMinutes = 0L;
    private String    deathCause       = "";
    private String    killerName       = "";
    @Nullable private CompoundTag recordingNbt = null;

    public MemorialBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MEMORIAL_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public boolean bindToEssence(ItemStack essence) {
        CompoundTag tag = essence.getTag();
        if (tag == null) return false;

        String owner = tag.getString("OwnerName");
        if (owner.isEmpty()) owner = tag.getString("ghostName");
        if (owner.isEmpty()) return false;

        this.boundOwner       = owner;
        this.banishedAt       = tag.getLong("BanishedAt");
        this.ownerLifeMinutes = tag.getLong("LivedMinutes");
        this.deathCause       = tag.getString("DeathCause");
        this.killerName       = tag.getString("KillerName");
        this.recordingNbt     = tag.contains("Recording")
                                ? tag.getCompound("Recording").copy()
                                : null;
        setChanged();
        return true;
    }

    public boolean     isBound()         { return !boundOwner.isEmpty(); }
    public String      getBoundOwner()   { return boundOwner; }
    public long        getBanishedAt()   { return banishedAt; }
    public String      getDeathCause()   { return deathCause; }
    public String      getKillerName()   { return killerName; }
    public boolean     hasRecording()    { return recordingNbt != null; }
    @Nullable
    public CompoundTag getRecordingNbt() { return recordingNbt; }

    public Component getStatusMessage(long currentGameTime) {
        if (!isBound()) {
            return Component.translatable("block.ghostofyou.memorial_block.unbound");
        }
        long hoursAgo = (currentGameTime - banishedAt) / 24000L;
        return Component.translatable("block.ghostofyou.memorial_block.status",
                boundOwner, ownerLifeMinutes, hoursAgo);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  MemorialBlockEntity be) {
        if (!be.isBound()) return;
        long gameTick = level.getGameTime();

        if (gameTick % 40 == 0 && level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL,
                    pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    2, 0.2, 0.1, 0.2, 0.01);
        }

        if (gameTick % 20 == 0) {
            AABB area = new AABB(pos).inflate(5.0);
            for (Player player : level.getEntitiesOfClass(Player.class, area)) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false));
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_BOUND_OWNER,        boundOwner);
        tag.putLong  (NBT_BANISHED_AT,        banishedAt);
        tag.putLong  (NBT_OWNER_LIFE_MINUTES, ownerLifeMinutes);
        tag.putString(NBT_DEATH_CAUSE,        deathCause);
        tag.putString(NBT_KILLER_NAME,        killerName);
        if (recordingNbt != null) {
            tag.put(NBT_RECORDING, recordingNbt.copy());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boundOwner       = tag.getString(NBT_BOUND_OWNER);
        banishedAt       = tag.getLong  (NBT_BANISHED_AT);
        ownerLifeMinutes = tag.getLong  (NBT_OWNER_LIFE_MINUTES);
        deathCause       = tag.getString(NBT_DEATH_CAUSE);
        killerName       = tag.getString(NBT_KILLER_NAME);
        recordingNbt     = tag.contains(NBT_RECORDING)
                           ? tag.getCompound(NBT_RECORDING).copy()
                           : null;
    }
}
