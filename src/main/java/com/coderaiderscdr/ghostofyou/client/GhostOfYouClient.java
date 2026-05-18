package com.coderaiderscdr.ghostofyou.client;

import com.coderaiderscdr.ghostofyou.block.ModBlocks;
import com.coderaiderscdr.ghostofyou.entity.ModEntities;
import com.coderaiderscdr.ghostofyou.client.render.GhostEntityRenderer;
import com.coderaiderscdr.ghostofyou.client.render.MemorialBlockEntityRenderer;
import com.coderaiderscdr.ghostofyou.client.render.SoulCrystalRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class GhostOfYouClient {

    private GhostOfYouClient() {}

    public static void register(IEventBus modBus) {
        modBus.register(GhostOfYouClient.class);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GHOST.get(), GhostEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_CRYSTAL.get(), SoulCrystalRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.MEMORIAL_BLOCK_ENTITY_TYPE.get(), MemorialBlockEntityRenderer::new);
    }
}