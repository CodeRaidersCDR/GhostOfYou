package com.coderaiderscdr.ghostofyou.client.render;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Renders {@link GhostEntity} as a translucent player-shaped figure. */
public class GhostEntityRenderer extends LivingEntityRenderer<GhostEntity, PlayerModel<GhostEntity>> {

    private static final ResourceLocation GHOST_TEXTURE =
            new ResourceLocation(GhostOfYou.MOD_ID, "textures/entity/ghost.png");

    public GhostEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);

        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                Minecraft.getInstance().getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(GhostEntity entity) {
        return GHOST_TEXTURE;
    }

    @Override
    public void render(GhostEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        int maxDist = ConfigManager.renderDistance();
        if (this.entityRenderDispatcher.distanceToSqr(entity) > (double) (maxDist * maxDist)) {
            return;
        }

        if (entity.isDying()) {
            float progress  = entity.getDyingProgress();
            final float dyingAlpha = ConfigManager.ghostTransparency() * (1.0f - progress);
            if (dyingAlpha < 0.005f) return;

            poseStack.pushPose();
            poseStack.translate(0.0, -progress * 0.5, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees(progress * 90f));

            MultiBufferSource dyingSource = renderType ->
                    new AlphaVertexConsumer(bufferSource.getBuffer(renderType), dyingAlpha);
            super.render(entity, entityYaw, partialTick, poseStack, dyingSource, packedLight);
            poseStack.popPose();
            return;
        }

        final float alpha = ConfigManager.ghostTransparency();
        MultiBufferSource alphaSource = renderType ->
                new AlphaVertexConsumer(bufferSource.getBuffer(renderType), alpha);
        super.render(entity, entityYaw, partialTick, poseStack, alphaSource, packedLight);
    }

    @Override
    protected @Nullable RenderType getRenderType(GhostEntity entity, boolean bodyVisible,
                                                  boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(GHOST_TEXTURE);
    }

    @Override
    protected boolean shouldShowName(GhostEntity entity) {
        return true;
    }
}
