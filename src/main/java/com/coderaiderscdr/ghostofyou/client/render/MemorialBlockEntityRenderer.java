package com.coderaiderscdr.ghostofyou.client.render;

import com.coderaiderscdr.ghostofyou.GhostOfYou;
import com.coderaiderscdr.ghostofyou.block.MemorialBlock;
import com.coderaiderscdr.ghostofyou.block.entity.MemorialBlockEntity;
import com.coderaiderscdr.ghostofyou.entity.GhostEntity;
import com.coderaiderscdr.ghostofyou.entity.ModEntities;
import com.coderaiderscdr.ghostofyou.recording.Frame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemorialBlockEntityRenderer implements BlockEntityRenderer<MemorialBlockEntity> {

    private static final ResourceLocation GHOST_TEXTURE =
            new ResourceLocation(GhostOfYou.MOD_ID, "textures/entity/ghost.png");

    private static final float TEXT_BASE_SCALE = 0.016f;

    private static final float TEXT_MAX_WIDTH = 39f;

    private static final float LINE_HEIGHT = 10f;

    private static final float TEXT_Y = 0.44f;

    private static final float CAUSE_Y_TOP = 0.30f;

    private static final float FACE_Z = 3.0f / 16.0f - 0.0005f;

    private static final int FULL_BRIGHT = 0xF000F0;

    private static final float GHOST_BASE_Y = 1.0f;

    private static final int DEATH_ANIM_TICKS   = 20;
    private static final int DEATH_PAUSE_TICKS  = 40;
    private static final int RESPAWN_FADE_TICKS = 15;

    private final PlayerModel<GhostEntity> ghostModel;

    @org.jetbrains.annotations.Nullable
    private GhostEntity fakeGhost;

    private final Map<BlockPos, MiniPlaybackState> playbackCache = new HashMap<>();

    public MemorialBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.ghostModel = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
    }

    @Override
    public void render(MemorialBlockEntity be, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int overlay) {
        if (!be.isBound()) return;

        renderNickText(be, partialTick, poseStack, buffers, packedLight);
        renderMiniGhost(be, partialTick, poseStack, buffers, packedLight);
    }

    private void renderNickText(MemorialBlockEntity be, float partialTick,
                                PoseStack poseStack, MultiBufferSource buffers,
                                int packedLight) {
        Font font = Minecraft.getInstance().font;
        String owner = be.getBoundOwner();
        if (owner.isEmpty()) return;

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(MemorialBlock.FACING)
                ? state.getValue(MemorialBlock.FACING) : Direction.NORTH;
        float facingYRot = switch (facing) {
            case SOUTH -> 180f;
            case WEST  -> 270f;
            case EAST  ->  90f;
            default    ->   0f;
        };

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facingYRot));
        poseStack.translate(-0.5, 0.0, -0.5);

        float nameWidth = font.width(owner);
        float nameScale = TEXT_BASE_SCALE * Math.min(1.0f, TEXT_MAX_WIDTH / Math.max(1f, nameWidth));

        poseStack.pushPose();
        poseStack.translate(0.5, TEXT_Y, FACE_Z);
        poseStack.scale(-nameScale, -nameScale, nameScale);
        drawOutlinedText(font, owner, -nameWidth * 0.5f, 0f,
                0xFFFF5555, 0xFF440000,
                poseStack.last().pose(), buffers, FULL_BRIGHT);
        poseStack.popPose();

        String causeKey = be.getDeathCause();
        if (!causeKey.isEmpty() && !causeKey.equals("unknown")) {
            String killer = be.getKillerName();

            String causeRaw;
            try {
                causeRaw = Component.translatable(
                        "death.attack." + causeKey,
                        "",
                        killer.isEmpty() ? "" : killer
                ).getString().strip();

                while (causeRaw.contains("  ")) causeRaw = causeRaw.replace("  ", " ");

            } catch (Exception e) {
                causeRaw = causeKey;
            }

            List<String> lines = wordWrap(font, causeRaw, (int)(TEXT_MAX_WIDTH * 2.0f / 1.5f));

            float causeScale = TEXT_BASE_SCALE * (0.85f / 2.0f);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                float lineWidth = font.width(line);
                float lineY = CAUSE_Y_TOP - i * LINE_HEIGHT * causeScale;

                poseStack.pushPose();
                poseStack.translate(0.5, lineY, FACE_Z);
                poseStack.scale(-causeScale, -causeScale, causeScale);
                drawOutlinedText(font, line, -lineWidth * 0.5f, 0f,
                        0xFFFFFFFF, 0xFF333333,
                        poseStack.last().pose(), buffers, FULL_BRIGHT);
                poseStack.popPose();
            }
        }

        poseStack.popPose();
    }

    private static List<String> wordWrap(Font font, String text, int maxWidthPx) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) <= maxWidthPx) {
                current = new StringBuilder(candidate);
            } else {
                if (!current.isEmpty()) result.add(current.toString());
                current = new StringBuilder(word);
                if (result.size() >= 3) break;
            }
        }
        if (!current.isEmpty() && result.size() < 4) result.add(current.toString());
        return result;
    }

    private static void drawOutlinedText(Font font, String text,
                                         float x, float y,
                                         int textColor, int outColor,
                                         Matrix4f matrix,
                                         MultiBufferSource buffers,
                                         int light) {
        float off = 0.5f;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                font.drawInBatch(text, x + dx * off, y + dy * off, outColor,
                        false, matrix, buffers,
                        Font.DisplayMode.SEE_THROUGH, 0, light);
            }
        }
        font.drawInBatch(text, x, y, textColor,
                false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, light);
    }

    private void renderMiniGhost(MemorialBlockEntity be, float partialTick,
                                 PoseStack poseStack, MultiBufferSource buffers,
                                 int packedLight) {
        if (!be.hasRecording()) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockPos pos = be.getBlockPos();
        MiniPlaybackState state = playbackCache.computeIfAbsent(pos,
                k -> new MiniPlaybackState(MiniPlayback.parse(be.getRecordingNbt()), pos));

        if (state.playback == null && be.hasRecording()) {
            state = new MiniPlaybackState(MiniPlayback.parse(be.getRecordingNbt()), pos);
            playbackCache.put(pos, state);
        }

        MiniPlayback pb = state.playback;
        if (pb == null || pb.frameCount == 0) return;

        long gameTick = level.getGameTime();

        int cycleTicks = pb.totalTicks + DEATH_ANIM_TICKS + DEATH_PAUSE_TICKS + RESPAWN_FADE_TICKS;
        int t = (int)(gameTick % cycleTicks);

        float alpha;
        int   frameIndex;
        float deathRotation = 0f;

        if (t < pb.totalTicks) {

            frameIndex    = pb.currentFrame(gameTick, partialTick);
            alpha         = 0.75f;
            deathRotation = 0f;
        } else if (t < pb.totalTicks + DEATH_ANIM_TICKS) {

            frameIndex = pb.frameCount - 1;
            float progress = (t - pb.totalTicks + partialTick) / (float) DEATH_ANIM_TICKS;
            deathRotation = progress * 90f;
            alpha = 0.75f * (1.0f - progress);
        } else if (t < pb.totalTicks + DEATH_ANIM_TICKS + DEATH_PAUSE_TICKS) {

            frameIndex                = 0;
            alpha                     = 0f;
            deathRotation             = 0f;
            state.walkDistance        = 0f;
            state.smoothedSwingAmount = 0f;
        } else {

            frameIndex = 0;
            float progress = (t - pb.totalTicks - DEATH_ANIM_TICKS - DEATH_PAUSE_TICKS + partialTick)
                             / (float) RESPAWN_FADE_TICKS;
            alpha                     = 0.75f * progress;
            deathRotation             = 0f;
            state.walkDistance        = 0f;
            state.smoothedSwingAmount = 0f;
        }

        float relX = pb.relX[frameIndex];
        float relY = pb.relY[frameIndex];
        float relZ = pb.relZ[frameIndex];
        float ghostYaw  = pb.yaw[frameIndex];
        byte  ghostFlags = pb.flags[frameIndex];

        if (t < pb.totalTicks && frameIndex + 1 < pb.frameCount) {
            int curTick  = frameIndex > 0 ? pb.tickAtFrame[frameIndex - 1] : 0;
            int nextTick = pb.tickAtFrame[frameIndex];
            int span     = nextTick - curTick;
            if (span > 0) {
                int elapsed = (t % pb.totalTicks) - curTick;
                float lerp  = Math.max(0f, Math.min(1f, (elapsed + partialTick) / span));
                relX += (pb.relX[frameIndex + 1] - relX) * lerp;
                relY += (pb.relY[frameIndex + 1] - relY) * lerp;
                relZ += (pb.relZ[frameIndex + 1] - relZ) * lerp;

                float yawDiff = pb.yaw[frameIndex + 1] - ghostYaw;
                if (yawDiff >  180) yawDiff -= 360;
                if (yawDiff < -180) yawDiff += 360;
                ghostYaw += yawDiff * lerp;
            }
        }

        float dh = 0f;
        if (t < pb.totalTicks) {
            dh = Math.abs(frameIndex > 0 ? pb.relX[frameIndex] - pb.relX[frameIndex - 1] : 0f)
               + Math.abs(frameIndex > 0 ? pb.relZ[frameIndex] - pb.relZ[frameIndex - 1] : 0f);
            state.walkDistance += dh * 15f;
        }
        float targetSwingAmount   = (t < pb.totalTicks) ? Math.min(1.0f, dh * 20f) : 0f;
        state.smoothedSwingAmount = state.smoothedSwingAmount * 0.8f + targetSwingAmount * 0.2f;
        float limbSwing           = state.walkDistance;
        float limbSwingAmount     = state.smoothedSwingAmount;

        ghostModel.crouching = (ghostFlags & Frame.FLAG_SNEAK) != 0;

        if (fakeGhost == null) {
            fakeGhost = new GhostEntity(ModEntities.GHOST.get(), level);
        }
        fakeGhost.setYRot(ghostYaw);

        poseStack.pushPose();

        poseStack.translate(0.5 + relX, GHOST_BASE_Y + relY, 0.5 + relZ);
        poseStack.scale(MiniPlayback.MINI_SCALE, MiniPlayback.MINI_SCALE, MiniPlayback.MINI_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - ghostYaw));

        if (deathRotation != 0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(deathRotation));
        }

        poseStack.scale(-1f, -1f, 1f);
        poseStack.translate(0.0, -1.501, 0.0);

        float ageInTicks = (gameTick % 1000) + partialTick;
        ghostModel.setupAnim(fakeGhost, limbSwing, limbSwingAmount, ageInTicks, 0f, 0f);

        VertexConsumer vc = new AlphaVertexConsumer(
                buffers.getBuffer(RenderType.entityTranslucent(GHOST_TEXTURE)), alpha);
        ghostModel.renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, alpha);

        poseStack.popPose();
    }

    private static final class MiniPlaybackState {
        final MiniPlayback playback;
        final BlockPos     pos;
        float walkDistance        = 0f;
        float smoothedSwingAmount = 0f;

        MiniPlaybackState(MiniPlayback playback, BlockPos pos) {
            this.playback = playback;
            this.pos      = pos;
        }
    }
}
