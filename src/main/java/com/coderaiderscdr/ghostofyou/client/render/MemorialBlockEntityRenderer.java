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

import java.util.HashMap;
import java.util.Map;

/**
 * Renders the Memorial Block's overlay:
 *
 * <ol>
 *   <li><b>Always (when bound)</b>: Billboard-style glowing red nickname text
 *       above the block. Font scale is reduced for long names so the text
 *       never overflows the block's horizontal extent.</li>
 *   <li><b>When essence is bound</b>: Smaller cause-of-death text below the
 *       nickname.</li>
 *   <li><b>When recording is present</b>: A 28%-scale translucent ghost model
 *       that loops through the recorded death positions above the block.</li>
 * </ol>
 */
public class MemorialBlockEntityRenderer implements BlockEntityRenderer<MemorialBlockEntity> {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private static final ResourceLocation GHOST_TEXTURE =
            new ResourceLocation(GhostOfYou.MOD_ID, "textures/entity/ghost.png");

    /**
     * Base text scale in block units per font pixel.
     * 1 font pixel = TEXT_BASE_SCALE block units.
     * (Minecraft sign renderer uses ~0.010417)
     */
    private static final float TEXT_BASE_SCALE = 0.011f;

    /**
     * Maximum font width (in font pixels) before the nickname is scaled down.
     * The stele front face is 10/16 = 0.625 blocks wide;
     * 0.625 / 0.011 ≈ 56 font pixels.
     */
    private static final float TEXT_MAX_WIDTH = 56f;

    /** Block-local Y position for the nickname line. */
    private static final float TEXT_Y = 0.40f;

    /** Block-local Y position for the cause-of-death line (below nick). */
    private static final float CAUSE_Y = 0.26f;

    /**
     * Z offset from the block origin to the front face of the stele model
     * (model coord 3/16), pushed 1 mm in front so it doesn't z-fight.
     */
    private static final float FACE_Z = 3.0f / 16.0f - 0.001f;

    /** Y offset (block-local) for the mini ghost's foot position (= block top + small margin). */
    private static final float GHOST_BASE_Y = 1.0f;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final PlayerModel<GhostEntity> ghostModel;

    /**
     * Dummy GhostEntity used solely as a non-null argument for
     * {@link PlayerModel#setupAnim} — the model calls {@code entity.isCrouching()},
     * {@code entity.isPassenger()}, etc.  Created lazily once the client level is
     * available.
     */
    @org.jetbrains.annotations.Nullable
    private GhostEntity fakeGhost;

    /**
     * Per-block playback state; keyed by BlockPos.
     * Each entry is rebuilt whenever the recording changes.
     */
    private final Map<BlockPos, MiniPlaybackState> playbackCache = new HashMap<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public MemorialBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.ghostModel = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
    }

    // -----------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------

    @Override
    public void render(MemorialBlockEntity be, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int overlay) {
        if (!be.isBound()) return;

        renderNickText(be, partialTick, poseStack, buffers, packedLight);
        renderMiniGhost(be, partialTick, poseStack, buffers, packedLight);
    }

    // -----------------------------------------------------------------------
    // Sign-style nick text (+ cause-of-death line) on the block's front face
    // -----------------------------------------------------------------------

    private void renderNickText(MemorialBlockEntity be, float partialTick,
                                PoseStack poseStack, MultiBufferSource buffers,
                                int packedLight) {
        Font font = Minecraft.getInstance().font;
        String owner = be.getBoundOwner();
        if (owner.isEmpty()) return;

        // Rotate coordinate system to match block's FACING so the text
        // ends up on whichever face the block is oriented towards.
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(MemorialBlock.FACING)
                ? state.getValue(MemorialBlock.FACING) : Direction.NORTH;
        float facingYRot = switch (facing) {
            case SOUTH -> 180f;
            case WEST  -> 270f;
            case EAST  ->  90f;
            default    ->   0f;  // NORTH = no rotation
        };

        poseStack.pushPose();
        // Rotate around block centre to align with facing direction
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facingYRot));
        poseStack.translate(-0.5, 0.0, -0.5);

        // --- Nickname line ---
        float textWidth = font.width(owner);
        float scale = TEXT_BASE_SCALE * Math.min(1.0f, TEXT_MAX_WIDTH / textWidth);

        poseStack.pushPose();
        poseStack.translate(0.5, TEXT_Y, FACE_Z);
        // Sign-style flip: -X so text reads left-to-right, -Y so font is upright
        poseStack.scale(-scale, -scale, scale);
        drawOutlinedText(font, owner, -textWidth * 0.5f, 0f,
                0xFFFF5555, 0xFF000000, poseStack.last().pose(), buffers, packedLight);
        poseStack.popPose();

        // --- Cause-of-death line (only when cause known) ---
        String cause = be.getDeathCause();
        if (!cause.isEmpty() && !cause.equals("unknown")) {
            String killer   = be.getKillerName();
            String causeStr;
            try {
                // death.attack.<cause> uses %1$s = victim, %2$s = killer
                causeStr = Component.translatable(
                        "death.attack." + cause,
                        owner,
                        killer.isEmpty() ? "" : killer
                ).getString();
            } catch (Exception e) {
                causeStr = cause;
            }

            float causeWidth = font.width(causeStr);
            float causeScale = TEXT_BASE_SCALE
                    * Math.min(1.0f, TEXT_MAX_WIDTH / Math.max(1f, causeWidth))
                    * 0.70f;

            poseStack.pushPose();
            poseStack.translate(0.5, CAUSE_Y, FACE_Z);
            poseStack.scale(-causeScale, -causeScale, causeScale);
            drawOutlinedText(font, causeStr, -causeWidth * 0.5f, 0f,
                    0xFFAAAAAA, 0xFF000000, poseStack.last().pose(), buffers, packedLight);
            poseStack.popPose();
        }

        poseStack.popPose(); // facing rotation
    }

    /**
     * Draws text with an 8-directional outline for a sign-like glow effect.
     *
     * @param font      Minecraft font renderer
     * @param text      string to draw
     * @param x, y      position in font units
     * @param textColor ARGB main text colour
     * @param outColor  ARGB outline colour
     */
    private static void drawOutlinedText(Font font, String text,
                                         float x, float y,
                                         int textColor, int outColor,
                                         Matrix4f matrix,
                                         MultiBufferSource buffers,
                                         int packedLight) {
        float off = 0.6f; // offset for outline passes (font units)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                font.drawInBatch(text, x + dx * off, y + dy * off, outColor,
                        false, matrix, buffers,
                        Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            }
        }
        font.drawInBatch(text, x, y, textColor,
                false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, packedLight);
    }

    // -----------------------------------------------------------------------
    // Mini ghost rendering
    // -----------------------------------------------------------------------

    private void renderMiniGhost(MemorialBlockEntity be, float partialTick,
                                 PoseStack poseStack, MultiBufferSource buffers,
                                 int packedLight) {
        if (!be.hasRecording()) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        // Get or rebuild playback state for this block position
        BlockPos pos = be.getBlockPos();
        MiniPlaybackState state = playbackCache.computeIfAbsent(pos,
                k -> new MiniPlaybackState(MiniPlayback.parse(be.getRecordingNbt()), pos));

        // If the recording has changed (block was re-bound), refresh
        if (state.playback == null || (be.hasRecording() && state.playback == null)) {
            state = new MiniPlaybackState(MiniPlayback.parse(be.getRecordingNbt()), pos);
            playbackCache.put(pos, state);
        }

        MiniPlayback pb = state.playback;
        if (pb == null || pb.frameCount == 0) return;

        long gameTick = level.getGameTime();
        int frameIndex = pb.currentFrame(gameTick, partialTick);

        float relX = pb.relX[frameIndex];
        float relY = pb.relY[frameIndex];
        float relZ = pb.relZ[frameIndex];
        float ghostYaw = pb.yaw[frameIndex];
        byte  ghostFlags = pb.flags[frameIndex];

        // Interpolate position between current and next frame
        if (frameIndex + 1 < pb.frameCount) {
            int curTick = frameIndex > 0 ? pb.tickAtFrame[frameIndex - 1] : 0;
            int nextTick = pb.tickAtFrame[frameIndex];
            int span = nextTick - curTick;
            if (span > 0) {
                int elapsed = (int)(gameTick % pb.totalTicks) - curTick;
                float t = (elapsed + partialTick) / span;
                t = Math.max(0f, Math.min(1f, t));
                relX = relX + (pb.relX[frameIndex + 1] - relX) * t;
                relY = relY + (pb.relY[frameIndex + 1] - relY) * t;
                relZ = relZ + (pb.relZ[frameIndex + 1] - relZ) * t;
            }
        }

        // Compute walk animation from horizontal delta
        float dxHoriz = Math.abs(frameIndex > 0 ? (pb.relX[frameIndex] - pb.relX[frameIndex - 1]) : 0)
                      + Math.abs(frameIndex > 0 ? (pb.relZ[frameIndex] - pb.relZ[frameIndex - 1]) : 0);
        state.walkDistance += dxHoriz * 15f;
        float limbSwing       = state.walkDistance;
        float limbSwingAmount = Math.min(1.0f, dxHoriz * 20f);

        // Setup model pose
        boolean crouching = (ghostFlags & Frame.FLAG_SNEAK) != 0;
        ghostModel.crouching = crouching;

        poseStack.pushPose();

        // Position: centred above the block, plus Recording-relative offset
        poseStack.translate(
                0.5 + relX,
                GHOST_BASE_Y + relY,
                0.5 + relZ);

        poseStack.scale(MiniPlayback.MINI_SCALE, MiniPlayback.MINI_SCALE, MiniPlayback.MINI_SCALE);

        // Face in the recorded yaw direction
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - ghostYaw));

        // Standard MC entity model flip: required for PlayerModel to render right-side-up
        // in a block entity renderer (same as LivingEntityRenderer applies before model draw)
        poseStack.scale(-1f, -1f, 1f);

        // Shift model origin so feet land at GHOST_BASE_Y
        poseStack.translate(0.0, -1.501, 0.0);

        float ageInTicks = (gameTick % 1000) + partialTick;

        // Ensure we have a fake ghost entity (needed by PlayerModel.setupAnim)
        if (fakeGhost == null) {
            fakeGhost = new GhostEntity(ModEntities.GHOST.get(), level);
        }
        fakeGhost.setYRot(ghostYaw);
        ghostModel.setupAnim(fakeGhost, limbSwing, limbSwingAmount, ageInTicks, 0f, 0f);

        // Render with translucent ghost texture
        VertexConsumer vc = new AlphaVertexConsumer(
                buffers.getBuffer(RenderType.entityTranslucent(GHOST_TEXTURE)), 0.7f);
        ghostModel.renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY,
                1f, 1f, 1f, 0.7f);

        poseStack.popPose();
    }

    // -----------------------------------------------------------------------
    // Inner helpers
    // -----------------------------------------------------------------------

    /** Mutable per-block animation state. */
    private static final class MiniPlaybackState {
        final MiniPlayback playback;
        final BlockPos     pos;
        float walkDistance = 0f;

        MiniPlaybackState(MiniPlayback playback, BlockPos pos) {
            this.playback = playback;
            this.pos      = pos;
        }
    }
}
