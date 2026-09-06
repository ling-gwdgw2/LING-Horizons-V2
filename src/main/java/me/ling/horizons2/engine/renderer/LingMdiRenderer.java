package me.ling.horizons2.engine.renderer;

import me.ling.horizons2.engine.voxel.VoxelPalette;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * High-performance GPU-Driven Multi-Draw Indirect (MDI) Renderer for LING Horizons 2.0.
 *
 * Supports dual-pass rendering:
 * 1. Opaque Pass (solid blocks with depth write)
 * 2. Translucent Pass (water, stained glass with depth testing and alpha blending)
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

    public static class RenderPassBuffers implements AutoCloseable {
        private final GlSsbo sectionBuffer = new GlSsbo();
        private final GlSsbo commandBuffer = new GlSsbo();
        private final GlSsbo counterBuffer = new GlSsbo();
        private final GlSsbo quadBuffer = new GlSsbo();
        private final GlSsbo originBuffer = new GlSsbo();
        private int totalSections = 0;

        public void upload(List<SectionRenderData> sections) {
            if (sections == null) {
                this.totalSections = 0;
                return;
            }
            this.totalSections = sections.size();
            if (this.totalSections == 0) return;

            int totalQuads = 0;
            for (SectionRenderData sec : sections) {
                totalQuads += sec.quadCount;
            }

            int[] sectionBufferData = new int[totalSections * 12];
            float[] originBufferData = new float[totalSections * 4];
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

                originBufferData[i * 4 + 0] = (float) minX;
                originBufferData[i * 4 + 1] = (float) minY;
                originBufferData[i * 4 + 2] = (float) minZ;
                originBufferData[i * 4 + 3] = 0.0f;

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
            commandBuffer.uploadData(new int[totalSections * 4], GL15.GL_DYNAMIC_DRAW);
        }

        public int getTotalSections() {
            return totalSections;
        }

        @Override
        public void close() {
            sectionBuffer.close();
            commandBuffer.close();
            counterBuffer.close();
            quadBuffer.close();
            originBuffer.close();
            totalSections = 0;
        }
    }

    private GlProgram cullProgram;
    private GlProgram rasterProgram;
    private GlSsbo materialColorBuffer;
    private LingHiZBuffer hiZBuffer;

    private RenderPassBuffers opaquePass;
    private RenderPassBuffers translucentPass;

    private int dummyVao = 0;
    private boolean initialized = false;

    public void initialize() {
        if (initialized) return;

        dummyVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(dummyVao);
        GL30.glBindVertexArray(0);

        String cullSrc = loadShaderSource("/assets/ling_horizons2/shaders/ling_cull.comp");
        String vertSrc = loadShaderSource("/assets/ling_horizons2/shaders/ling_quad.vert");
        String fragSrc = loadShaderSource("/assets/ling_horizons2/shaders/ling_quad.frag");
        String hizSrc = loadShaderSource("/assets/ling_horizons2/shaders/ling_hiz_downsample.comp");

        cullProgram = GlProgram.createCompute(cullSrc);
        rasterProgram = GlProgram.createRaster(vertSrc, fragSrc);
        hiZBuffer = new LingHiZBuffer();
        hiZBuffer.initialize(hizSrc);

        materialColorBuffer = new GlSsbo();
        opaquePass = new RenderPassBuffers();
        translucentPass = new RenderPassBuffers();

        updateMaterialColors(VoxelPalette.getInstance());
        initialized = true;
    }

    public void updateMaterialColors(VoxelPalette palette) {
        if (palette == null || materialColorBuffer == null) return;
        float[] colors = new float[VoxelPalette.MAX_MATERIALS * 4];
        for (int i = 0; i < VoxelPalette.MAX_MATERIALS; i++) {
            if (i == 0) {
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
            float a = palette.isTranslucent(i) ? 0.65f : 1.0f;

            colors[i * 4 + 0] = r;
            colors[i * 4 + 1] = g;
            colors[i * 4 + 2] = b;
            colors[i * 4 + 3] = a;
        }
        materialColorBuffer.uploadData(floatArrayToIntArray(colors), GL15.GL_STATIC_DRAW);
    }

    public void uploadSectionData(List<SectionRenderData> opaqueSections, List<SectionRenderData> translucentSections) {
        if (!initialized) return;
        if (opaquePass != null) opaquePass.upload(opaqueSections);
        if (translucentPass != null) translucentPass.upload(translucentSections);
    }

    private static int[] floatArrayToIntArray(float[] floats) {
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
        float m00 = m.m00(), m10 = m.m10(), m20 = m.m20(), m30 = m.m30();
        float m01 = m.m01(), m11 = m.m11(), m21 = m.m21(), m31 = m.m31();
        float m02 = m.m02(), m12 = m.m12(), m22 = m.m22(), m32 = m.m32();
        float m03 = m.m03(), m13 = m.m13(), m23 = m.m23(), m33 = m.m33();

        planes[0] = new Vector4f(m03 + m00, m13 + m10, m23 + m20, m33 + m30);
        planes[1] = new Vector4f(m03 - m00, m13 - m10, m23 - m20, m33 - m30);
        planes[2] = new Vector4f(m03 + m01, m13 + m11, m23 + m21, m33 + m31);
        planes[3] = new Vector4f(m03 - m01, m13 - m11, m23 - m21, m33 - m31);
        planes[4] = new Vector4f(m03 + m02, m13 + m12, m23 + m22, m33 + m32);
        planes[5] = new Vector4f(m03 - m02, m13 - m12, m23 - m22, m33 - m32);

        for (int i = 0; i < 6; i++) {
            Vector4f p = planes[i];
            float len = (float) Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z);
            if (len > 1e-6f) {
                p.div(len);
            }
        }
        return planes;
    }

    public void updateHiZ(int sourceDepthTexture, int screenWidth, int screenHeight) {
        if (hiZBuffer != null && initialized) {
            hiZBuffer.buildPyramid(sourceDepthTexture, screenWidth, screenHeight);
        }
    }

    public void renderOpaque(Matrix4f viewProj, Vector3f cameraPos, float curvatureRadius, Vector4f fogColor, float fogStart, float fogEnd, Vector3f sunDir, Vector4f[] frustumPlanes, boolean useHiZ) {
        if (!initialized || opaquePass == null || opaquePass.totalSections == 0) return;
        renderPassInternal(opaquePass, false, viewProj, cameraPos, curvatureRadius, fogColor, fogStart, fogEnd, sunDir, frustumPlanes, useHiZ);
    }

    public void renderTranslucent(Matrix4f viewProj, Vector3f cameraPos, float curvatureRadius, Vector4f fogColor, float fogStart, float fogEnd, Vector3f sunDir, Vector4f[] frustumPlanes, boolean useHiZ) {
        if (!initialized || translucentPass == null || translucentPass.totalSections == 0) return;
        renderPassInternal(translucentPass, true, viewProj, cameraPos, curvatureRadius, fogColor, fogStart, fogEnd, sunDir, frustumPlanes, useHiZ);
    }

    private void renderPassInternal(RenderPassBuffers pass, boolean translucent, Matrix4f viewProj, Vector3f cameraPos, float curvatureRadius, Vector4f fogColor, float fogStart, float fogEnd, Vector3f sunDir, Vector4f[] frustumPlanes, boolean useHiZ) {
        // 0. Save OpenGL state to avoid leaking into Sodium, ImmediatelyFast, or Vanilla Minecraft
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTexture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevIndirectBuffer = GL11.glGetInteger(GL43.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        try {
            // 1. GPU Compute Culling Pass
            cullProgram.bind();

            pass.sectionBuffer.bindBase(0);
            pass.commandBuffer.bindBase(1);
            pass.counterBuffer.bindBase(2);

            pass.counterBuffer.uploadData(new int[] { 0 }, GL15.GL_DYNAMIC_DRAW);

            for (int i = 0; i < 6; i++) {
                Vector4f plane = frustumPlanes[i];
                cullProgram.setUniformVec4("uFrustumPlanes[" + i + "]", plane.x, plane.y, plane.z, plane.w);
            }
            cullProgram.setUniformVec3("uCameraPos", cameraPos.x, cameraPos.y, cameraPos.z);
            cullProgram.setUniformInt("uTotalSections", pass.totalSections);

            // Hi-Z occlusion culling parameters
            boolean hizActive = useHiZ && hiZBuffer != null && hiZBuffer.isReady();
            if (hizActive) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, hiZBuffer.getHizTexture());
                cullProgram.setUniformInt("uUseHiZ", 1);
                cullProgram.setUniformInt2("uHiZSize", hiZBuffer.getWidth(), hiZBuffer.getHeight());
                cullProgram.setUniformInt("uHiZMaxLevel", hiZBuffer.getLevels() - 1);
            } else {
                cullProgram.setUniformInt("uUseHiZ", 0);
                cullProgram.setUniformInt2("uHiZSize", 1, 1);
                cullProgram.setUniformInt("uHiZMaxLevel", 0);
            }
            cullProgram.setUniformMatrix4("uViewProjMatrix", viewProj);

            int workgroups = (pass.totalSections + 63) / 64;
            GL43.glDispatchCompute(workgroups, 1, 1);
            GL43.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            cullProgram.unbind();
            checkGl("after cull dispatch");

            // Explicitly unbind texture 0 after culling so raster pass and subsequent passes don't inherit it
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            // 2. GPU Rasterization Pass
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            if (translucent) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDepthMask(false); // Don't write depth for translucent to allow layered blending
            } else {
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDepthMask(true);
            }

            GL11.glDisable(GL11.GL_CULL_FACE);

            GL30.glBindVertexArray(dummyVao);
            rasterProgram.bind();

            pass.quadBuffer.bindBase(3);
            pass.originBuffer.bindBase(4);
            materialColorBuffer.bindBase(5);

            rasterProgram.setUniformMatrix4("uViewProjMatrix", viewProj);
            rasterProgram.setUniformVec3("uCameraPos", cameraPos.x, cameraPos.y, cameraPos.z);
            rasterProgram.setUniformFloat("uCurvatureRadius", curvatureRadius);
            rasterProgram.setUniformVec4("uFogColor", fogColor.x, fogColor.y, fogColor.z, fogColor.w);
            rasterProgram.setUniformFloat("uFogStart", fogStart);
            rasterProgram.setUniformFloat("uFogEnd", fogEnd);
            rasterProgram.setUniformVec3("uSunDirection", sunDir.x, sunDir.y, sunDir.z);

            pass.commandBuffer.bindAsIndirectBuffer();
            GL43.glMultiDrawArraysIndirect(GL11.GL_TRIANGLES, 0, pass.totalSections, 16);
            checkGl("after indirect draw");

            GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
            GL30.glBindVertexArray(0);
            rasterProgram.unbind();
        } finally {
            // Unbind all SSBOs so they never leak into Minecraft or Sodium
            for (int b = 0; b < 6; b++) {
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, b, 0);
            }

            // Restore indirect buffer & VAO
            GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, prevIndirectBuffer);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuffer);
            GL30.glBindVertexArray(prevVao);

            // Restore program
            GL20.glUseProgram(prevProgram);

            // Restore texture unit 0
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture0);
            GL13.glActiveTexture(prevActiveTexture);

            // Restore render flags
            if (prevBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            if (prevDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(prevDepthMask);
            checkGl("after renderPassInternal cleanup");
        }
    }

    private static void checkGl(String label) {
        int err = GL11.glGetError();
        if (err != GL11.GL_NO_ERROR) {
            me.ling.horizons2.LingHorizons2.LOGGER.error("[LING GL ERROR] At '{}': 0x{} ({})", label, Integer.toHexString(err), err);
        }
    }

    public int getTotalSections() {
        return (opaquePass != null ? opaquePass.totalSections : 0) + (translucentPass != null ? translucentPass.totalSections : 0);
    }

    public int getOpaqueSections() {
        return opaquePass != null ? opaquePass.totalSections : 0;
    }

    public int getTranslucentSections() {
        return translucentPass != null ? translucentPass.totalSections : 0;
    }

    public void clear() {
        if (opaquePass != null) opaquePass.upload(null);
        if (translucentPass != null) translucentPass.upload(null);
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
        if (materialColorBuffer != null) materialColorBuffer.close();
        if (hiZBuffer != null) {
            hiZBuffer.close();
            hiZBuffer = null;
        }
        if (opaquePass != null) opaquePass.close();
        if (translucentPass != null) translucentPass.close();
        initialized = false;
    }
}
