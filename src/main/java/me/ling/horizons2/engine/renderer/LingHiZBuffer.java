package me.ling.horizons2.engine.renderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

/**
 * GPU Hierarchical Depth Pyramid (Hi-Z Buffer) Generator for LING Horizons 2.0.
 *
 * Generates a conservative MAX-depth mipmap pyramid from the Minecraft scene depth buffer
 * using GPU Compute Shaders (zero FBO switching, pure parallel downsampling).
 */
public class LingHiZBuffer implements AutoCloseable {
    private int hizTexture = 0;
    private int width = 0;
    private int height = 0;
    private int levels = 0;
    private GlProgram downsampleProgram;
    private boolean initialized = false;

    public void initialize(String downsampleShaderSrc) {
        if (initialized) return;
        this.downsampleProgram = GlProgram.createCompute(downsampleShaderSrc);
        this.initialized = true;
    }

    private void ensureStorage(int screenWidth, int screenHeight) {
        int targetW = Math.max(1, Integer.highestOneBit(screenWidth));
        int targetH = Math.max(1, Integer.highestOneBit(screenHeight));

        if (targetW == width && targetH == height && hizTexture != 0) {
            return;
        }

        if (hizTexture != 0) {
            GL11.glDeleteTextures(hizTexture);
            hizTexture = 0;
        }

        this.width = targetW;
        this.height = targetH;
        this.levels = Math.max(1, (int) Math.floor(Math.log(Math.max(width, height)) / Math.log(2)) + 1);

        this.hizTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hizTexture);
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, levels, GL30.GL_R32F, width, height);

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public void buildPyramid(int sourceDepthTexture, int screenWidth, int screenHeight) {
        if (!initialized || sourceDepthTexture <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        // Validate that sourceDepthTexture is actually a valid OpenGL texture
        if (!GL11.glIsTexture(sourceDepthTexture)) {
            return;
        }

        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTexture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        try {
            ensureStorage(screenWidth, screenHeight);

            downsampleProgram.bind();

            // Pass 0: Downsample source depth buffer into Hi-Z level 0
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceDepthTexture);
            GL42.glBindImageTexture(0, hizTexture, 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_R32F);

            downsampleProgram.setUniformInt("uSrcSampler", 0);
            downsampleProgram.setUniformInt("uSrcLevel", -1);
            downsampleProgram.setUniformInt2("uDstSize", width, height);

            int wgX = (width + 15) / 16;
            int wgY = (height + 15) / 16;
            GL43.glDispatchCompute(wgX, wgY, 1);
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
            checkGl("after hiz pass 0");

            // Unbind sampler texture 0 immediately: passes 1..levels-1 use imageLoad
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            // Subsequent passes: Downsample level i-1 to level i using explicit image units (zero sampler hazard)
            int curW = width;
            int curH = height;

            for (int i = 1; i < levels; i++) {
                curW = Math.max(1, curW / 2);
                curH = Math.max(1, curH / 2);

                GL42.glBindImageTexture(1, hizTexture, i - 1, false, 0, GL15.GL_READ_ONLY, GL30.GL_R32F);
                GL42.glBindImageTexture(0, hizTexture, i, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_R32F);
                downsampleProgram.setUniformInt("uSrcLevel", i - 1);
                downsampleProgram.setUniformInt2("uDstSize", curW, curH);

                wgX = (curW + 15) / 16;
                wgY = (curH + 15) / 16;
                GL43.glDispatchCompute(wgX, wgY, 1);
                GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
            }
            checkGl("after hiz pyramid downsamples");
        } finally {
            GL42.glBindImageTexture(0, 0, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R32F);
            GL42.glBindImageTexture(1, 0, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R32F);
            GL20.glUseProgram(prevProgram);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture0);
            GL13.glActiveTexture(prevActiveTexture);
            checkGl("after hiz buildPyramid cleanup");
        }
    }

    private static void checkGl(String label) {
        int err = GL11.glGetError();
        if (err != GL11.GL_NO_ERROR) {
            me.ling.horizons2.LingHorizons2.LOGGER.error("[LING GL ERROR HiZ] At '{}': 0x{} ({})", label, Integer.toHexString(err), err);
        }
    }

    public int getHizTexture() {
        return hizTexture;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLevels() {
        return levels;
    }

    public boolean isReady() {
        return initialized && hizTexture != 0;
    }

    @Override
    public void close() {
        if (hizTexture != 0) {
            GL11.glDeleteTextures(hizTexture);
            hizTexture = 0;
        }
        if (downsampleProgram != null) {
            downsampleProgram.close();
            downsampleProgram = null;
        }
        initialized = false;
        width = 0;
        height = 0;
        levels = 0;
    }
}
