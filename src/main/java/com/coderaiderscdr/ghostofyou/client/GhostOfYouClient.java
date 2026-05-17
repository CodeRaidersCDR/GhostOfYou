package com.coderaiderscdr.ghostofyou.client;

import com.coderaiderscdr.ghostofyou.entity.ModEntities;
import com.coderaiderscdr.ghostofyou.client.render.GhostEntityRenderer;
import com.coderaiderscdr.ghostofyou.client.render.SoulCrystalRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-only initialisation for Ghost of You.
 * Registered via {@link com.coderaiderscdr.ghostofyou.GhostOfYou} using
 * {@code DistExecutor} to avoid loading client classes on the server.
 */
public final class GhostOfYouClient {

    private GhostOfYouClient() {}

    /**
     * Register all client-side listeners on the mod event bus.
     * Must be called from within a {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} block.
     *
     * @param modBus the mod's event bus
     */
    public static void register(IEventBus modBus) {
        modBus.register(GhostOfYouClient.class);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GHOST.get(), GhostEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_CRYSTAL.get(), SoulCrystalRenderer::new);
    }
}
