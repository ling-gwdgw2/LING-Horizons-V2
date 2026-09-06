package me.ling.horizons2.engine.renderer;

import me.ling.horizons2.engine.voxel.VoxelPalette;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * High-performance GPU-Driven Multi-Draw Indirect (MDI) Renderer for LING Horizons 2.0.
 *
 * Executes view frustum culling on the GPU via compute shaders and dispatches
 * all visible section geometry in a single glMultiDrawArraysIndirect call using GL_TRIANGLES.
 */
public class LingMdiRenderer implements AutoCloseable {
    public static class SectionRenderData {
        public final int sx, sy, sz;
        public final long[] quads;
        public final int quadCount;

        public SectionRenderData(int sx, int sy, int sz, long[] quads, int quadCount) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.quads = quads;
            this.quadCount = quadCount;
        }
    }

    private GlProgram cullProgram;
    private GlProgram rasterProgram;

    private GlSsbo sectionBuffer;
    private GlSsbo commandBuffer;
    private GlSsbo counterBuffer;
    private GlSsbo quadBuffer;
    private GlSsbo originBuffer;
    private GlSsbo materialColorBuffer;

    private int dummyVao = 0;
    private int totalSections = 0;
    private boolean initialized = false;

    public void initialize() {
        if (initialized) return;

        // OpenGL Core Profile requires a bound VAO even for vertex-less draws
        dummyVao = GL30.glGenVertexArrays();

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
        updateMaterialColors(VoxelPalette.getInstance());

        initialized = true;
    }

    public void updateMaterialColors(VoxelPalette palette) {
        if (palette == null || materialColorBuffer == null) return;
        float[] colors = new float[VoxelPalette.MAX_MATERIALS * 4];
        for (int i = 0; i < VoxelPalette.MAX_MATERIALS; i++) {
            if (i == 0) {
                // Air is fully transparent
                colors[0] = 0.0f;
                colors[1] = 0.0f;
                colors[2] = 0.0f;
                colors[3] = 0.0f;
                continue;
            }

            BlockState state = palette.getState(i);
            int rgb = 0x888888;
            if (state != null) {
                try {
                    MapColor mapColor = state.getMapColor(null, null);
                    if (mapColor != null) {
                        rgb = mapColor.col;
                    }
                } catch (Exception ignored) {}
            }

            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = (rgb & 0xFF) / 255.0f;
            float a = palette.isTranslucent(i) ? 0.7f : 1.0f;

            colors[i * 4 + 0] = r;
            colors[i * 4 + 1] = g;
            colors[i * 4 + 2] = b;
            colors[i * 4 + 3] = a;
        }
        materialColorBuffer.uploadData(floatArrayToIntArray(colors), GL15.GL_STATIC_DRAW);
    }

    /**
     * Uploads section bounding boxes, origins, and packed geometry to GPU SSBOs.
     */
    public void uploadSectionData(List<SectionRenderData> sections) {
        if (!initialized || sections == null) return;

        this.totalSections = sections.size();
        if (totalSections == 0) return;

        int totalQuads = 0;
        for (SectionRenderData sec : sections) {
            totalQuads += sec.quadCount;
        }

        int[] sectionBufferData = new int[totalSections * 12]; // 12 ints = 48 bytes per section
        float[] originBufferData = new float[totalSections * 4]; // 4 floats = 16 bytes per section
        long[] quadBufferData = new long[Math.max(1, totalQuads)];

        int currentQuadOffset = 0;
        for (int i = 0; i < totalSections; i++) {
            SectionRenderData sec = sections.get(i);
            int minX = sec.sx * 32;
            int minY = sec.sy * 32;
            int minZ = sec.sz * 32;
            int maxX = minX + 32;
            int maxY = minY + 32;
            int maxZ = minZ + 32;

            // Origins
            originBufferData[i * 4 + 0] = (float) minX;
            originBufferData[i * 4 + 1] = (float) minY;
            originBufferData[i * 4 + 2] = (float) minZ;
            originBufferData[i * 4 + 3] = 0.0f;

            // SectionDrawInfo (12 ints = 48 bytes)
            int base = i * 12;
            sectionBufferData[base + 0] = Float.floatToRawIntBits((float) minX);
            sectionBufferData[base + 1] = Float.floatToRawIntBits((float) minY);
            sectionBufferData[base + 2] = Float.floatToRawIntBits((float) minZ);
            sectionBufferData[base + 3] = Float.floatToRawIntBits(sec.quadCount > 0 ? 1.0f : 0.0f);

            sectionBufferData[base + 4] = Float.floatToRawIntBits((float) maxX);
            sectionBufferData[base + 5] = Float.floatToRawIntBits((float) maxY);
            sectionBufferData[base + 6] = Float.floatToRawIntBits((float) maxZ);
            sectionBufferData[base + 7] = Float.floatToRawIntBits((float) sec.quadCount);

            sectionBufferData[base + 8] = currentQuadOffset;
            sectionBufferData[base + 9] = sec.quadCount;
            sectionBufferData[base + 10] = 0;
            sectionBufferData[base + 11] = 0;

            if (sec.quadCount > 0 && sec.quads != null) {
                System.arraycopy(sec.quads, 0, quadBufferData, currentQuadOffset, sec.quadCount);
            }
            currentQuadOffset += sec.quadCount;
        }

        sectionBuffer.uploadData(sectionBufferData, GL15.GL_DYNAMIC_DRAW);
        originBuffer.uploadData(floatArrayToIntArray(originBufferData), GL15.GL_DYNAMIC_DRAW);
        quadBuffer.uploadData(quadBufferData, GL15.GL_DYNAMIC_DRAW);
        commandBuffer.uploadData(new int[totalSections * 4], GL15.GL_DYNAMIC_DRAW); // 4 uints = 16 bytes per command
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

    public static Vector4f[] extractFrustumPlanes(Matrix4f m) {
        Vector4f[] planes = new Vector4f[6];
        // Row vectors of Matrix4f
        float m00 = m.m00(), m10 = m.m10(), m20 = m.m20(), m30 = m.m30();
        float m01 = m.m01(), m11 = m.m11(), m21 = m.m21(), m31 = m.m31();
        float m02 = m.m02(), m12 = m.m12(), m22 = m.m22(), m32 = m.m32();
        float m03 = m.m03(), m13 = m.m13(), m23 = m.m23(), m33 = m.m33();

        planes[0] = new Vector4f(m03 + m00, m13 + m10, m23 + m20, m33 + m30); // Left
        planes[1] = new Vector4f(m03 - m00, m13 - m10, m23 - m20, m33 - m30); // Right
        planes[2] = new Vector4f(m03 + m01, m13 + m11, m23 + m21, m33 + m31); // Bottom
        planes[3] = new Vector4f(m03 - m01, m13 - m11, m23 - m21, m33 - m31); // Top
        planes[4] = new Vector4f(m03 + m02, m13 + m12, m23 + m22, m33 + m32); // Near
        planes[5] = new Vector4f(m03 - m02, m13 - m12, m23 - m22, m33 - m32); // Far

        for (int i = 0; i < 6; i++) {
            Vector4f p = planes[i];
            float len = (float) Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z);
            if (len > 1e-6f) {
                p.div(len);
            }
        }
        return planes;
    }

    /**
     * Renders all active sections using GPU Compute culling and Multi-Draw Indirect.
     */
    public void render(Matrix4f viewProj, Vector3f cameraPos, float curvatureRadius, Vector4f fogColor, float fogStart, float fogEnd, Vector3f sunDir, Vector4f[] frustumPlanes) {
        if (!initialized || totalSections == 0) return;

        // 1. GPU Compute Culling Pass
        cullProgram.bind();

        sectionBuffer.bindBase(0);
        commandBuffer.bindBase(1);
        counterBuffer.bindBase(2);

        // Reset visible counter to 0
        counterBuffer.uploadData(new int[] { 0 }, GL15.GL_DYNAMIC_DRAW);

        // Upload Uniforms for Culling
        for (int i = 0; i < 6; i++) {
            Vector4f plane = frustumPlanes[i];
            cullProgram.setUniformVec4("uFrustumPlanes[" + i + "]", plane.x, plane.y, plane.z, plane.w);
        }
        cullProgram.setUniformVec3("uCameraPos", cameraPos.x, cameraPos.y, cameraPos.z);
        cullProgram.setUniformInt("uTotalSections", totalSections);

        // Dispatch Compute (64 sections per workgroup)
        int workgroups = (totalSections + 63) / 64;
        GL43.glDispatchCompute(workgroups, 1, 1);

        // Memory Barrier: Ensure indirect commands and SSBO writes are visible to rasterizer
        GL43.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        cullProgram.unbind();

        // 2. GPU Rasterization Pass (Multi-Draw Indirect)
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL30.glBindVertexArray(dummyVao);
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

        // OpenGL Core Profile: GL_TRIANGLES with 16-byte stride (DrawArraysIndirectCommand: 4 uints)
        GL43.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES, 0, totalSections, 16);

        // Cleanup
        GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
        GL30.glBindVertexArray(0);
        rasterProgram.unbind();

        if (cullWasEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    public void setTotalSections(int totalSections) {
        this.totalSections = totalSections;
    }

    public int getTotalSections() {
        return totalSections;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void close() {
        if (dummyVao != 0) {
            GL30.glDeleteVertexArrays(dummyVao);
            dummyVao = 0;
        }
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
