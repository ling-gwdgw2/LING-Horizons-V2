package me.ling.horizons2.engine.renderer.iris;

import me.ling.horizons2.LingHorizons2;
import me.ling.horizons2.engine.renderer.LingMdiRenderer;
import me.ling.horizons2.engine.renderer.LingMdiRenderer.SectionRenderData;
import me.ling.horizons2.engine.renderer.pipeline.IRenderPipeline;
import me.ling.horizons2.engine.renderer.pipeline.RenderContext;

import java.util.List;

/**
 * Dedicated Render Pipeline for Iris Shaderpacks in LING Horizons 2.0.
 *
 * Fully decoupled from VanillaRenderPipeline to facilitate independent
 * shaderpack feature development (G-buffer interception, AST patching, shadow passes).
 */
public class IrisRenderPipeline implements IRenderPipeline {
    private final LingMdiRenderer mdiRenderer = new LingMdiRenderer();
    private boolean shaderpackWasActive = false;

    @Override
    public void initialize() {
        mdiRenderer.initialize();
        LingHorizons2.LOGGER.info("[LING Horizons 2.0] IrisRenderPipeline initialized successfully.");
    }

    @Override
    public void renderOpaque(RenderContext ctx) {
        if (!mdiRenderer.isInitialized() || mdiRenderer.getOpaqueSections() == 0) return;

        boolean shaderpackActive = IrisCompatHelper.isShaderpackActive();
        if (shaderpackActive != shaderpackWasActive) {
            shaderpackWasActive = shaderpackActive;
            LingHorizons2.LOGGER.info("[LING Horizons 2.0] Shaderpack state changed: active = {}", shaderpackActive);
        }

        // Hi-Z occlusion culling update
        if (ctx.useHiZ() && ctx.depthTextureId() > 0) {
            mdiRenderer.updateHiZ(ctx.depthTextureId(), ctx.screenWidth(), ctx.screenHeight());
        }

        // Render opaque geometry through MDI pipeline
        // Future developers can hook custom G-buffer bindings and AST patched programs here
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

        // Render translucent geometry (water, glass)
        // Future developers can hook gbuffers_water blending and refraction targets here
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
        return "Iris-Shaderpack-MDI";
    }

    public LingMdiRenderer getMdiRenderer() {
        return mdiRenderer;
    }

    @Override
    public void close() {
        mdiRenderer.close();
    }
}
