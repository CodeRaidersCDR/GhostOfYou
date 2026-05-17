package com.coderaiderscdr.ghostofyou.block;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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

    /** Memorial Block — decorative block crafted from Ghost Essence + Stone. */
    public static final RegistryObject<Block> MEMORIAL_BLOCK =
            BLOCKS.register("memorial_block",
                    () -> new MemorialBlock(Block.Properties.of()
                            .strength(1.5f, 6.0f)
                            .requiresCorrectToolForDrops()));

    // Block item (registered separately so it can be placed in the item tab)
    public static final RegistryObject<Item> MEMORIAL_BLOCK_ITEM =
            BLOCK_ITEMS.register("memorial_block",
                    () -> new BlockItem(MEMORIAL_BLOCK.get(), new Item.Properties()));
}
