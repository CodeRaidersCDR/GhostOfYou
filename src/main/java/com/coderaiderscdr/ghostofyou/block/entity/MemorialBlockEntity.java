package com.coderaiderscdr.ghostofyou.block.entity;

import com.coderaiderscdr.ghostofyou.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Block entity for the Memorial Block.
 *
 * <p>When bound to a Ghost Essence (via right-click), the block:
 * <ol>
 *   <li>Stores the essence owner's name, lifetime and banishment time.</li>
 *   <li>Periodically emits a soft soul-particle aura.</li>
 *   <li>Gives nearby players Resistance I and Speed I once per second.</li>
 * </ol>
 */
public class MemorialBlockEntity extends BlockEntity {

    private static final String NBT_BOUND_OWNER       = "BoundOwner";
    private static final String NBT_BANISHED_AT        = "BanishedAt";
    private static final String NBT_OWNER_LIFE_MINUTES = "OwnerLifeMinutes";

    private String boundOwner        = "";
    private long   banishedAt        = 0L;
    private long   ownerLifeMinutes  = 0L;

    public MemorialBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MEMORIAL_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Binding
    // ------------------------------------------------------------------

    /**
     * Bind this memorial to a Ghost Essence item.
     *
     * @param essence the essence ItemStack (must contain NBT written by GhostInteraction)
     * @return {@code true} if binding succeeded
     */
    public boolean bindToEssence(ItemStack essence) {
        CompoundTag tag = essence.getTag();
        if (tag == null) return false;
        String owner = tag.getString("OwnerName");
        if (owner.isEmpty()) {
            // Legacy key fallback
            owner = tag.getString("ghostName");
        }
        if (owner.isEmpty()) return false;

        this.boundOwner       = owner;
        this.banishedAt       = tag.getLong("BanishedAt");
        this.ownerLifeMinutes = tag.getLong("LivedMinutes");
        setChanged();
        return true;
    }

    public boolean isBound()        { return !boundOwner.isEmpty(); }
    public String  getBoundOwner()  { return boundOwner; }
    public long    getBanishedAt()  { return banishedAt; }

    /** Human-readable status line shown on right-click. */
    public Component getStatusMessage(long currentGameTime) {
        if (!isBound()) {
            return Component.translatable("block.ghostofyou.memorial_block.unbound");
        }
        long hoursAgo = (currentGameTime - banishedAt) / 24000L; // 1 MC day ≈ 20 min real
        return Component.translatable("block.ghostofyou.memorial_block.status",
                boundOwner, ownerLifeMinutes, hoursAgo);
    }

    // ------------------------------------------------------------------
    // Server tick: aura particles + buff nearby players
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  MemorialBlockEntity be) {
        if (!be.isBound()) return;

        long gameTick = level.getGameTime();

        // Soul particle aura every 40 ticks (2 s)
        if (gameTick % 40 == 0 && level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL,
                    pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    2, 0.2, 0.1, 0.2, 0.01);
        }

        // Buff players within 5 blocks — refresh every second (20 ticks)
        if (gameTick % 20 == 0) {
            AABB area = new AABB(pos).inflate(5.0);
            for (Player player : level.getEntitiesOfClass(Player.class, area)) {
                // duration=40 (2 s) so it refreshes every second without visual gaps
                player.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false));
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, 40, 0, true, false));
            }
        }
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_BOUND_OWNER,       boundOwner);
        tag.putLong  (NBT_BANISHED_AT,       banishedAt);
        tag.putLong  (NBT_OWNER_LIFE_MINUTES, ownerLifeMinutes);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boundOwner       = tag.getString(NBT_BOUND_OWNER);
        banishedAt       = tag.getLong(NBT_BANISHED_AT);
        ownerLifeMinutes = tag.getLong(NBT_OWNER_LIFE_MINUTES);
    }
}
