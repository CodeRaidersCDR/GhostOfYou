package com.coderaiderscdr.ghostofyou.client.render;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.config.ConfigManager;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Renders {@link GhostEntity} as a translucent player-shaped figure.
 */
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

        double visualX = entity.hasPlaybackRenderPosition() ? entity.getPlaybackRenderX() : entity.getX();
        double visualY = entity.hasPlaybackRenderPosition() ? entity.getPlaybackRenderY() : entity.getY();
        double visualZ = entity.hasPlaybackRenderPosition() ? entity.getPlaybackRenderZ() : entity.getZ();

        int maxDist = ConfigManager.renderDistance();
        if (this.entityRenderDispatcher.distanceToSqr(visualX, visualY, visualZ) > (double) (maxDist * maxDist)) {
            return;
        }

        float alpha = ConfigManager.ghostTransparency();
        MultiBufferSource alphaSource = renderType -> {
            VertexConsumer consumer = bufferSource.getBuffer(renderType);
            return new AlphaVertexConsumer(consumer, alpha);
        };

        poseStack.pushPose();
        if (entity.hasPlaybackRenderPosition()) {
            double vanillaX = Mth.lerp((double) partialTick, entity.xOld, entity.getX());
            double vanillaY = Mth.lerp((double) partialTick, entity.yOld, entity.getY());
            double vanillaZ = Mth.lerp((double) partialTick, entity.zOld, entity.getZ());
            poseStack.translate(visualX - vanillaX, visualY - vanillaY, visualZ - vanillaZ);
        }
        super.render(entity, entityYaw, partialTick, poseStack, alphaSource, packedLight);
        poseStack.popPose();
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
