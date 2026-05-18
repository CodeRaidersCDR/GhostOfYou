package com.coderaiderscdr.ghostofyou.block;

import com.coderaiderscdr.ghostofyou.block.entity.MemorialBlockEntity;
import com.coderaiderscdr.ghostofyou.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Memorial Block — a persistent tribute to a banished ghost.
 *
 * <p>Right-click with a {@link ModItems#GHOST_ESSENCE Ghost Essence} to bind
 * the block to that ghost's data. Once bound, the block periodically emits
 * soul particles and grants nearby players a small Resistance + Speed buff.
 * Right-click with an empty hand to read the memorial's inscription.
 */
public class MemorialBlock extends BaseEntityBlock {

    public MemorialBlock(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // Block Entity
    // ------------------------------------------------------------------

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MemorialBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlocks.MEMORIAL_BLOCK_ENTITY_TYPE.get(),
                MemorialBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // use the normal block model, not INVISIBLE
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MemorialBlockEntity memorial)) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() == ModItems.GHOST_ESSENCE.get()) {
            // Bind the memorial to this essence
            if (memorial.bindToEssence(held)) {
                if (!player.isCreative()) held.shrink(1);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "block.ghostofyou.memorial_block.bound",
                                memorial.getBoundOwner()),
                        true);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }

        if (held.isEmpty()) {
            // Show the memorial inscription
            player.sendSystemMessage(memorial.getStatusMessage(level.getGameTime()));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
