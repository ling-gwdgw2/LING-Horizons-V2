package me.ling.horizons2.engine.renderer;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Modern OpenGL 4.6 Shader Storage Buffer Object (SSBO) wrapper with Direct State Access (DSA).
 */
public class GlSsbo implements AutoCloseable {
    private final int bufferId;
    private long sizeBytes;

    public GlSsbo() {
        this.bufferId = GL45.glCreateBuffers();
    }

    public void uploadData(ByteBuffer data, int usage) {
        this.sizeBytes = data.remaining();
        GL45.glNamedBufferData(bufferId, data, usage);
    }

    public void uploadData(long[] data, int usage) {
        this.sizeBytes = (long) data.length * 8;
        GL45.glNamedBufferData(bufferId, data, usage);
    }

    public void uploadData(int[] data, int usage) {
        this.sizeBytes = (long) data.length * 4;
        GL45.glNamedBufferData(bufferId, data, usage);
    }

    public void bindBase(int bindingIndex) {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingIndex, bufferId);
    }

    public void bindAsIndirectBuffer() {
        GL15.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, bufferId);
    }

    public int getBufferId() {
        return bufferId;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    @Override
    public void close() {
        GL15.glDeleteBuffers(bufferId);
    }
}
