package me.ling.horizons2.engine.renderer;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Modern OpenGL 4.6 Shader Program Wrapper for LING Horizons 2.0.
 */
public class GlProgram implements AutoCloseable {
    private final int programId;

    public GlProgram() {
        this.programId = GL20.glCreateProgram();
    }

    public static GlProgram createCompute(String computeSource) {
        GlProgram prog = new GlProgram();
        int computeShader = compileShader(GL43.GL_COMPUTE_SHADER, computeSource);
        GL20.glAttachShader(prog.programId, computeShader);
        prog.link();
        GL20.glDeleteShader(computeShader);
        return prog;
    }

    public static GlProgram createRaster(String vertSource, String fragSource) {
        GlProgram prog = new GlProgram();
        int vert = compileShader(GL20.GL_VERTEX_SHADER, vertSource);
        int frag = compileShader(GL20.GL_FRAGMENT_SHADER, fragSource);
        GL20.glAttachShader(prog.programId, vert);
        GL20.glAttachShader(prog.programId, frag);
        prog.link();
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);
        return prog;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException("Shader compilation failed (" + type + "): " + log);
        }
        return shader;
    }

    public void link() {
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(programId);
            throw new RuntimeException("Program link failed: " + log);
        }
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int getUniformLocation(String name) {
        return GL20.glGetUniformLocation(programId, name);
    }

    public void setUniformMatrix4(String name, Matrix4f mat) {
        int loc = getUniformLocation(name);
        if (loc >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var fb = stack.mallocFloat(16);
                mat.get(fb);
                GL20.glUniformMatrix4fv(loc, false, fb);
            }
        }
    }

    public void setUniformVec3(String name, float x, float y, float z) {
        int loc = getUniformLocation(name);
        if (loc >= 0) GL20.glUniform3f(loc, x, y, z);
    }

    public void setUniformVec4(String name, float x, float y, float z, float w) {
        int loc = getUniformLocation(name);
        if (loc >= 0) GL20.glUniform4f(loc, x, y, z, w);
    }

    public void setUniformFloat(String name, float val) {
        int loc = getUniformLocation(name);
        if (loc >= 0) GL20.glUniform1f(loc, val);
    }

    public void setUniformInt(String name, int val) {
        int loc = getUniformLocation(name);
        if (loc >= 0) GL20.glUniform1i(loc, val);
    }

    @Override
    public void close() {
        GL20.glDeleteProgram(programId);
    }

    public int getProgramId() {
        return programId;
    }
}
