package me.ling.horizons2.engine.renderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * High-performance GPU-Driven Multi-Draw Indirect (MDI) Renderer for LING Horizons 2.0.
 *
 * Executes view frustum culling on the GPU via compute shaders and dispatches
 * all visible section geometry in a single glMultiDrawArraysIndirect call.
 */
public class LingMdiRenderer implements AutoCloseable {
    private GlProgram cullProgram;
    private GlProgram rasterProgram;

    private GlSsbo sectionBuffer;
    private GlSsbo commandBuffer;
    private GlSsbo counterBuffer;
    private GlSsbo quadBuffer;
    private GlSsbo originBuffer;
    private GlSsbo materialColorBuffer;

    private int totalSections = 0;
    private boolean initialized = false;

    public void initialize() {
        if (initialized) return;

        // Load Shaders
        String cullSrc = loadShaderSource("/assets/ling_horizons2/shaders/ling_cull.comp");
        String vertSrc = loadShaderSource("/assets/ling_horizons2/shaders/ling_quad.vert");
        String fragSrc = loadShaderSource("/assets/ling_horizons2/shaders/ling_quad.frag");

        cullProgram = GlProgram.createCompute(cullSrc);
        rasterProgram = GlProgram.createRaster(vertSrc, fragSrc);

        // Allocate SSBOs
        sectionBuffer = new GlSsbo();
        commandBuffer = new GlSsbo();
        counterBuffer = new GlSsbo();
        quadBuffer = new GlSsbo();
        originBuffer = new GlSsbo();
        materialColorBuffer = new GlSsbo();

        // Default Material Palette: 4096 color entries
        float[] defaultColors = new float[4096 * 4];
        for (int i = 0; i < 4096; i++) {
            defaultColors[i * 4 + 0] = 0.6f; // R
            defaultColors[i * 4 + 1] = 0.6f; // G
            defaultColors[i * 4 + 2] = 0.6f; // B
            defaultColors[i * 4 + 3] = 1.0f; // A
        }
        materialColorBuffer.uploadData(floatArrayToIntArray(defaultColors), GL15.GL_STATIC_DRAW);

        initialized = true;
    }

    private int[] floatArrayToIntArray(float[] floats) {
        int[] ints = new int[floats.length];
        for (int i = 0; i < floats.length; i++) {
            ints[i] = Float.floatToRawIntBits(floats[i]);
        }
        return ints;
    }

    private String loadShaderSource(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Shader resource not found: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read shader resource: " + resourcePath, e);
        }
    }

    /**
     * Renders all active sections using GPU Compute culling and Multi-Draw Indirect.
     */
    public void render(Matrix4f viewProj, Vector3f cameraPos, float curvatureRadius, Vector4f fogColor, float fogStart, float fogEnd, Vector3f sunDir, Vector4f[] frustumPlanes) {
        if (!initialized || totalSections == 0) return;

        // 1. GPU Compute Culling Pass
        cullProgram.bind();

        // Bind SSBOs for Culling
        sectionBuffer.bindBase(0);
        commandBuffer.bindBase(1);
        counterBuffer.bindBase(2);

        // Reset visible counter to 0
        counterBuffer.uploadData(new int[] { 0 }, GL15.GL_DYNAMIC_DRAW);

        // Upload Frustum Planes
        for (int i = 0; i < 6; i++) {
            Vector4f plane = frustumPlanes[i];
            cullProgram.setUniformVec4("uFrustumPlanes[" + i + "]", plane.x, plane.y, plane.z, plane.w);
        }
        cullProgram.setUniformInt("uTotalSections", totalSections);

        // Dispatch Compute (64 sections per workgroup)
        int workgroups = (totalSections + 63) / 64;
        GL43.glDispatchCompute(workgroups, 1, 1);

        // Memory Barrier: Ensure indirect commands and SSBO writes are visible to rasterizer
        GL43.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        cullProgram.unbind();

        // 2. GPU Rasterization Pass (Multi-Draw Indirect)
        rasterProgram.bind();

        // Bind Data SSBOs
        quadBuffer.bindBase(3);
        originBuffer.bindBase(4);
        materialColorBuffer.bindBase(5);

        // Set Uniforms
        rasterProgram.setUniformMatrix4("uViewProjMatrix", viewProj);
        rasterProgram.setUniformVec3("uCameraPos", cameraPos.x, cameraPos.y, cameraPos.z);
        rasterProgram.setUniformFloat("uCurvatureRadius", curvatureRadius);
        rasterProgram.setUniformVec4("uFogColor", fogColor.x, fogColor.y, fogColor.z, fogColor.w);
        rasterProgram.setUniformFloat("uFogStart", fogStart);
        rasterProgram.setUniformFloat("uFogEnd", fogEnd);
        rasterProgram.setUniformVec3("uSunDirection", sunDir.x, sunDir.y, sunDir.z);

        // Bind Indirect Command Buffer
        commandBuffer.bindAsIndirectBuffer();

        // Execute Multi-Draw Indirect
        // Stride is 20 bytes (sizeof(DrawElementsIndirectCommand) = 5 * 4 bytes)
        GL43.glMultiDrawArraysIndirect(GL11.GL_QUADS, 0, totalSections, 20);

        // Cleanup
        GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
        rasterProgram.unbind();
    }

    public void setTotalSections(int totalSections) {
        this.totalSections = totalSections;
    }

    public int getTotalSections() {
        return totalSections;
    }

    public GlSsbo getSectionBuffer() {
        return sectionBuffer;
    }

    public GlSsbo getCommandBuffer() {
        return commandBuffer;
    }

    public GlSsbo getQuadBuffer() {
        return quadBuffer;
    }

    public GlSsbo getOriginBuffer() {
        return originBuffer;
    }

    @Override
    public void close() {
        if (cullProgram != null) cullProgram.close();
        if (rasterProgram != null) rasterProgram.close();
        if (sectionBuffer != null) sectionBuffer.close();
        if (commandBuffer != null) commandBuffer.close();
        if (counterBuffer != null) counterBuffer.close();
        if (quadBuffer != null) quadBuffer.close();
        if (originBuffer != null) originBuffer.close();
        if (materialColorBuffer != null) materialColorBuffer.close();
        initialized = false;
    }
}
