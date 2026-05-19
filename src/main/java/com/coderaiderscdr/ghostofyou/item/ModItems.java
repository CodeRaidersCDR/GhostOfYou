package com.coderaiderscdr.ghostofyou.item;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GhostOfYou.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GhostOfYou.MOD_ID);

    public static final RegistryObject<Item> GHOST_BANISHER =
            ITEMS.register("ghost_banisher",
                    () -> new GhostBanisherItem(new Item.Properties()
                            .stacksTo(1)
                            .durability(50)
                            .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> GHOST_ESSENCE =
            ITEMS.register("ghost_essence",
                    () -> new GhostEssenceItem(new Item.Properties()
                            .stacksTo(16)));

    public static final RegistryObject<CreativeModeTab> GHOST_OF_YOU_TAB =
            CREATIVE_MODE_TABS.register("ghost_of_you", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ghostofyou"))
                    .icon(() -> new ItemStack(GHOST_ESSENCE.get()))
                    .displayItems((params, output) -> {
                        output.accept(GHOST_ESSENCE.get());
                        output.accept(GHOST_BANISHER.get());
                        output.accept(ModBlocks.MEMORIAL_BLOCK_ITEM.get());
                    })
                    .build());
}
