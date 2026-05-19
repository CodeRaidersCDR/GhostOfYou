package com.coderaiderscdr.ghostofyou.client.render;

import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

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

        boolean shouldGlow = ConfigManager.isGhostGlowing();
        if (shouldGlow != entity.isCurrentlyGlowing()) {
            entity.setGlowingTag(shouldGlow);
        }
    }
}

