package com.coderaiderscdr.ghostofyou.item;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers all items belonging to the Ghost of You mod.
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GhostOfYou.MOD_ID);

    /** Ghost Banisher — crafted weapon to permanently remove ghost entities. */
    public static final RegistryObject<Item> GHOST_BANISHER =
            ITEMS.register("ghost_banisher",
                    () -> new GhostBanisherItem(new Item.Properties()
                            .stacksTo(1)
                            .durability(50)
                            .rarity(Rarity.RARE)));

    /** Ghost Essence — dropped by banished ghosts, used to craft the Memorial Block. */
    public static final RegistryObject<Item> GHOST_ESSENCE =
            ITEMS.register("ghost_essence",
                    () -> new GhostEssenceItem(new Item.Properties()
                            .stacksTo(16)));
}
