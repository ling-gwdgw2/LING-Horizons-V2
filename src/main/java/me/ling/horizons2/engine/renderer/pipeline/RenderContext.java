package me.ling.horizons2.engine.renderer.pipeline;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Context object carrying all frame-level rendering state and parameters.
 * Decouples pipeline renderers from Minecraft-specific event structures.
 */
public record RenderContext(
    Matrix4f viewProjMatrix,
    Matrix4f modelViewMatrix,
    Matrix4f projectionMatrix,
    Vector3f cameraPos,
    float curvatureRadius,
    Vector4f fogColor,
    float fogStart,
    float fogEnd,
    Vector3f sunDir,
    Vector4f[] frustumPlanes,
    int depthTextureId,
    int screenWidth,
    int screenHeight,
    boolean useHiZ
) {}
