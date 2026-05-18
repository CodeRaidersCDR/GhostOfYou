package com.coderaiderscdr.ghostofyou.block;

import com.coderaiderscdr.ghostofyou.block.entity.MemorialBlockEntity;
import com.coderaiderscdr.ghostofyou.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    /** Horizontal facing direction (which way the front face points). */
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Compound VoxelShape approximating the 3D model:
     *  - base slab (1,0,1)-(15,2,15)
     *  - main stele body (3,2,3)-(13,9,13)
     *  - upper neck / ring (4,9,4)-(12,12,12)
     *  - top plate (2,12,2)-(14,14,14)
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box( 1,  0,  1, 15,  2, 15),
            Block.box( 3,  2,  3, 13,  9, 13),
            Block.box( 4,  9,  4, 12, 12, 12),
            Block.box( 2, 12,  2, 14, 14, 14));

    public MemorialBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front of the block faces the player who placed it
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
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
                level.sendBlockUpdated(pos, state, state, 3);
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
