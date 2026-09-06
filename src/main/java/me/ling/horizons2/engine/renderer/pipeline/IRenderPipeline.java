package me.ling.horizons2.engine.renderer.pipeline;

import me.ling.horizons2.engine.renderer.LingMdiRenderer.SectionRenderData;
import java.util.List;

/**
 * Common abstraction for LOD rendering pipelines (Vanilla, Iris, etc.).
 * Allows the engine to seamlessly switch between Vanilla and Shaderpack rendering.
 */
public interface IRenderPipeline extends AutoCloseable {
    /**
     * Initializes GPU buffers, shaders, and pipeline resources.
     */
    void initialize();

    /**
     * Renders solid/opaque LOD geometry.
     */
    void renderOpaque(RenderContext context);

    /**
     * Renders translucent LOD geometry (water, stained glass, ice).
     */
    void renderTranslucent(RenderContext context);

    /**
     * Synchronizes chunk section geometry data to GPU storage buffers.
     */
    void uploadSectionData(List<SectionRenderData> opaqueSections, List<SectionRenderData> translucentSections);

    /**
     * Clears all uploaded geometry data from the pipeline.
     */
    void clear();

    /**
     * Returns whether this pipeline is properly initialized and ready to render.
     */
    boolean isReady();

    /**
     * Returns the human-readable name of this pipeline.
     */
    String getName();

    @Override
    void close();
}
