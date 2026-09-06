package me.ling.horizons2.engine.renderer.pipeline;

import me.ling.horizons2.LingHorizons2;
import me.ling.horizons2.engine.renderer.LingMdiRenderer.SectionRenderData;
import me.ling.horizons2.engine.renderer.iris.IrisCompatHelper;
import me.ling.horizons2.engine.renderer.iris.IrisRenderPipeline;
import me.ling.horizons2.engine.renderer.vanilla.VanillaRenderPipeline;

import java.util.List;

/**
 * Dynamic Render Pipeline Manager for LING Horizons 2.0.
 *
 * Coordinates between VanillaRenderPipeline and IrisRenderPipeline:
 * 1. Automatically selects the active pipeline based on whether Iris shaderpacks are in use.
 * 2. Uploads section geometry to both pipelines so in-game shader toggling ('K' key) is instant without chunk popping.
 * 3. Prevents any Iris classes from being loaded if Iris is not installed.
 */
public class RenderPipelineManager implements AutoCloseable {
    private static final RenderPipelineManager INSTANCE = new RenderPipelineManager();

    public static RenderPipelineManager getInstance() {
        return INSTANCE;
    }

    private VanillaRenderPipeline vanillaPipeline;
    private IrisRenderPipeline irisPipeline;
    private boolean initialized = false;

    private RenderPipelineManager() {}

    public synchronized void initialize() {
        if (initialized) return;

        // 1. Initialize pure Vanilla & Sodium pipeline
        vanillaPipeline = new VanillaRenderPipeline();
        vanillaPipeline.initialize();

        // 2. Initialize Iris pipeline ONLY if Iris is present in the mod environment
        if (IrisCompatHelper.isIrisInstalled()) {
            try {
                irisPipeline = new IrisRenderPipeline();
                irisPipeline.initialize();
                LingHorizons2.LOGGER.info("[LING Horizons 2.0] Registered modular IrisRenderPipeline.");
            } catch (Throwable t) {
                LingHorizons2.LOGGER.warn("[LING Horizons 2.0] Failed to initialize Iris pipeline, defaulting to Vanilla: {}", t.getMessage());
                irisPipeline = null;
            }
        } else {
            irisPipeline = null;
            LingHorizons2.LOGGER.info("[LING Horizons 2.0] Iris not detected. Operating exclusively on pure VanillaRenderPipeline.");
        }

        initialized = true;
    }

    /**
     * Resolves the active pipeline according to runtime shaderpack activation.
     */
    public IRenderPipeline getActivePipeline() {
        if (!initialized) {
            initialize();
        }

        if (IrisCompatHelper.isShaderpackActive() && irisPipeline != null && irisPipeline.isReady()) {
            return irisPipeline;
        }
        return vanillaPipeline;
    }

    /**
     * Synchronizes chunk section geometry to the pipelines.
     */
    public void uploadSectionData(List<SectionRenderData> opaqueSections, List<SectionRenderData> translucentSections) {
        if (vanillaPipeline != null && vanillaPipeline.isReady()) {
            vanillaPipeline.uploadSectionData(opaqueSections, translucentSections);
        }
        if (irisPipeline != null && irisPipeline.isReady()) {
            irisPipeline.uploadSectionData(opaqueSections, translucentSections);
        }
    }

    public void renderOpaque(RenderContext context) {
        IRenderPipeline active = getActivePipeline();
        if (active != null && active.isReady()) {
            active.renderOpaque(context);
        }
    }

    public void renderTranslucent(RenderContext context) {
        IRenderPipeline active = getActivePipeline();
        if (active != null && active.isReady()) {
            active.renderTranslucent(context);
        }
    }

    public void clear() {
        if (vanillaPipeline != null) vanillaPipeline.clear();
        if (irisPipeline != null) irisPipeline.clear();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public VanillaRenderPipeline getVanillaPipeline() {
        return vanillaPipeline;
    }

    public IrisRenderPipeline getIrisPipeline() {
        return irisPipeline;
    }

    @Override
    public synchronized void close() {
        if (vanillaPipeline != null) {
            vanillaPipeline.close();
            vanillaPipeline = null;
        }
        if (irisPipeline != null) {
            irisPipeline.close();
            irisPipeline = null;
        }
        initialized = false;
    }
}
