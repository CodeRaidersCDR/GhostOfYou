package com.coderaiderscdr.ghostofyou;

import com.coderaiderscdr.ghostofyou.block.ModBlocks;
import com.coderaiderscdr.ghostofyou.config.ModConfig;
import com.coderaiderscdr.ghostofyou.entity.ModEntities;
import com.coderaiderscdr.ghostofyou.event.BlockActionHandler;
import com.coderaiderscdr.ghostofyou.event.PlayerDeathHandler;
import com.coderaiderscdr.ghostofyou.event.PlayerTickHandler;
import com.coderaiderscdr.ghostofyou.event.ServerTickHandler;
import com.coderaiderscdr.ghostofyou.item.ModItems;
import com.coderaiderscdr.ghostofyou.item.ModPotions;
import com.coderaiderscdr.ghostofyou.loot.ModLootModifiers;
import com.coderaiderscdr.ghostofyou.network.ModNetwork;
import com.coderaiderscdr.ghostofyou.sound.ModSounds;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GhostOfYou.MOD_ID)
public class GhostOfYou {

    public static final String MOD_ID = "ghostofyou";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public GhostOfYou() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModConfig.register(ModLoadingContext.get());

        ModItems.ITEMS.register(modBus);
        ModItems.CREATIVE_MODE_TABS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ITEMS.register(modBus);
        ModBlocks.BLOCK_ENTITY_TYPES.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModPotions.MOB_EFFECTS.register(modBus);
        ModPotions.POTIONS.register(modBus);
        ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modBus);

        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(PlayerDeathHandler.class);
        MinecraftForge.EVENT_BUS.register(PlayerTickHandler.class);
        MinecraftForge.EVENT_BUS.register(BlockActionHandler.class);
        MinecraftForge.EVENT_BUS.register(ServerTickHandler.class);

        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.coderaiderscdr.ghostofyou.client.GhostOfYouClient.register(modBus));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            ModPotions.registerBrewingRecipes();
        });
    }
}
