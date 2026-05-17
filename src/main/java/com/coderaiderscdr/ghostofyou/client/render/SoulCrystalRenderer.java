package com.coderaiderscdr.ghostofyou.client.render;

import com.coderaiderscdr.ghostofyou.entity.SoulCrystalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Renders a {@link SoulCrystalEntity} as a spinning, bobbing Nether Star
 * hovering at the player's death location.
 *
 * <p>The item is rendered full-bright so it is always visible regardless of
 * surrounding light level. Soul-fire particles are emitted server-side in
 * {@link SoulCrystalEntity#tick()} to create a glowing halo effect.
 */
public class SoulCrystalRenderer extends EntityRenderer<SoulCrystalEntity> {

    /** The item displayed as the crystal orb. */
    private static final ItemStack DISPLAY_ITEM = new ItemStack(Items.NETHER_STAR);

    /** Dummy texture — entity has no actual texture (all rendering done via ItemRenderer). */
    private static final ResourceLocation DUMMY =
            new ResourceLocation("minecraft", "textures/misc/white.png");

    /** Full-bright packed light value: sky=15, block=15. */
    private static final int FULL_BRIGHT = 0xF000F0;

    public SoulCrystalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f; // no shadow — orb is floating
    }

    @Override
    public void render(SoulCrystalEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // Smooth bobbing: ±0.12 blocks on a sine wave
        float bob = Mth.sin((entity.tickCount + partialTick) * 0.12f) * 0.12f;
        poseStack.translate(0.0, 1.0 + bob, 0.0);

        // Continuous spin around Y axis (3° per tick = 60 RPM)
        float angle = (entity.tickCount + partialTick) * 3f;
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));

        // Slight tilt for visual interest
        poseStack.mulPose(Axis.ZP.rotationDegrees(15f));

        poseStack.scale(0.7f, 0.7f, 0.7f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                DISPLAY_ITEM,
                ItemDisplayContext.GROUND,
                FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.level(),
                entity.getId());

        poseStack.popPose();

        // Render nametag (owner name shown above orb)
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulCrystalEntity entity) {
        return DUMMY;
    }
}
