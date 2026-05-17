package com.coderaiderscdr.ghostofyou.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Wraps a {@link VertexConsumer} and multiplies the alpha channel of every
 * vertex by a configurable factor.  Used by {@link GhostEntityRenderer} to
 * apply the configurable {@code ghostTransparency} value without custom shaders.
 *
 * <p>All other components (UV, normal, overlay, light) are forwarded unchanged.
 */
public class AlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alphaFactor;

    /**
     * @param delegate    the real {@link VertexConsumer} to forward vertices to
     * @param alphaFactor multiplied with every per-vertex alpha (0.0 – 1.0)
     */
    public AlphaVertexConsumer(VertexConsumer delegate, float alphaFactor) {
        this.delegate    = delegate;
        this.alphaFactor = alphaFactor;
    }

    // ------------------------------------------------------------------
    // Position + color (where we intercept alpha)
    // ------------------------------------------------------------------

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        delegate.color(r, g, b, (int) (a * alphaFactor));
        return this;
    }

    // ------------------------------------------------------------------
    // Pass-through for everything else
    // ------------------------------------------------------------------

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        delegate.defaultColor(r, g, b, (int) (a * alphaFactor));
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
