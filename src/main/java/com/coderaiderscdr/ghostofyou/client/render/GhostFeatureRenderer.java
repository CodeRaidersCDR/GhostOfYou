package com.coderaiderscdr.ghostofyou.client.render;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Optional feature layer for {@link GhostEntityRenderer}.
 *
 * <p>When {@code ghostGlowing} is enabled in the client config, this layer
 * ensures the ghost's glowing tag is set so Vanilla renders the outline effect
 * (no custom shaders needed — Vanilla outline renderer handles everything).
 */
public class GhostFeatureRenderer extends RenderLayer<GhostEntity, PlayerModel<GhostEntity>> {

    public GhostFeatureRenderer(RenderLayerParent<GhostEntity, PlayerModel<GhostEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, GhostEntity entity,
                       float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        // Synchronise the glow tag on the client based on config.
        // setGlowingTag sends a data sync that the outline renderer respects.
        // Only update when the state needs to change to avoid unnecessary packets.
        boolean shouldGlow = ConfigManager.isGhostGlowing();
        if (shouldGlow != entity.isCurrentlyGlowing()) {
            entity.setGlowingTag(shouldGlow);
        }
    }
}

