package me.ling.horizons2.engine.renderer.vanilla;

import me.ling.horizons2.engine.renderer.LingMdiRenderer;
import me.ling.horizons2.engine.renderer.LingMdiRenderer.SectionRenderData;
import me.ling.horizons2.engine.renderer.pipeline.IRenderPipeline;
import me.ling.horizons2.engine.renderer.pipeline.RenderContext;

import java.util.List;

/**
 * Pure Vanilla & Sodium Render Pipeline for LING Horizons 2.0.
 *
 * Operates independently with 0% Iris dependencies.
 * Uses GPU Multi-Draw Indirect (MDI), dynamic Hi-Z compute culling,
 * and custom standalone shaders.
 */
public class VanillaRenderPipeline implements IRenderPipeline {
    private final LingMdiRenderer mdiRenderer = new LingMdiRenderer();

    @Override
    public void initialize() {
        mdiRenderer.initialize();
    }

    @Override
    public void renderOpaque(RenderContext ctx) {
        if (!mdiRenderer.isInitialized() || mdiRenderer.getOpaqueSections() == 0) return;

        if (ctx.useHiZ() && ctx.depthTextureId() > 0) {
            mdiRenderer.updateHiZ(ctx.depthTextureId(), ctx.screenWidth(), ctx.screenHeight());
        }

        mdiRenderer.renderOpaque(
            ctx.viewProjMatrix(),
            ctx.cameraPos(),
            ctx.curvatureRadius(),
            ctx.fogColor(),
            ctx.fogStart(),
            ctx.fogEnd(),
            ctx.sunDir(),
            ctx.frustumPlanes(),
            ctx.useHiZ()
        );
    }

    @Override
    public void renderTranslucent(RenderContext ctx) {
        if (!mdiRenderer.isInitialized() || mdiRenderer.getTranslucentSections() == 0) return;

        mdiRenderer.renderTranslucent(
            ctx.viewProjMatrix(),
            ctx.cameraPos(),
            ctx.curvatureRadius(),
            ctx.fogColor(),
            ctx.fogStart(),
            ctx.fogEnd(),
            ctx.sunDir(),
            ctx.frustumPlanes(),
            ctx.useHiZ()
        );
    }

    @Override
    public void uploadSectionData(List<SectionRenderData> opaqueSections, List<SectionRenderData> translucentSections) {
        mdiRenderer.uploadSectionData(opaqueSections, translucentSections);
    }

    @Override
    public void clear() {
        mdiRenderer.clear();
    }

    @Override
    public boolean isReady() {
        return mdiRenderer.isInitialized();
    }

    @Override
    public String getName() {
        return "Vanilla-Sodium-MDI";
    }

    public LingMdiRenderer getMdiRenderer() {
        return mdiRenderer;
    }

    @Override
    public void close() {
        mdiRenderer.close();
    }
}
