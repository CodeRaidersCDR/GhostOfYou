package com.coderaiderscdr.ghostofyou.block;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.block.entity.MemorialBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers all blocks (and their corresponding block items) for Ghost of You.
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, GhostOfYou.MOD_ID);

    public static final DeferredRegister<Item> BLOCK_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GhostOfYou.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, GhostOfYou.MOD_ID);

    /** Memorial Block — crafted from Soul Soil + Ghost Essence + Polished Blackstone. */
    public static final RegistryObject<Block> MEMORIAL_BLOCK =
            BLOCKS.register("memorial_block",
                    () -> new MemorialBlock(Block.Properties.of()
                            .strength(1.5f, 6.0f)
                            .requiresCorrectToolForDrops()));

    // Block item (registered separately so it can be placed in the item tab)
    public static final RegistryObject<Item> MEMORIAL_BLOCK_ITEM =
            BLOCK_ITEMS.register("memorial_block",
                    () -> new BlockItem(MEMORIAL_BLOCK.get(), new Item.Properties()));

    /** Block entity type for {@link MemorialBlock}. */
    public static final RegistryObject<BlockEntityType<MemorialBlockEntity>> MEMORIAL_BLOCK_ENTITY_TYPE =
            BLOCK_ENTITY_TYPES.register("memorial_block",
                    () -> BlockEntityType.Builder
                            .of(MemorialBlockEntity::new, MEMORIAL_BLOCK.get())
                            .build(null));
}
