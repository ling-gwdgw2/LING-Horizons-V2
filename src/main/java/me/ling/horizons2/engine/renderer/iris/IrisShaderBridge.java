package me.ling.horizons2.engine.renderer.iris;

/**
 * Foundation bridge defining the Iris Shaderpack rendering contract.
 *
 * Provides symbolic constants and integration points for:
 * 1. Iris G-Buffer targets (colortex0, colortex1, depthtex0, etc.)
 * 2. Standard Iris Uniform variables
 * 3. Future shaderpack AST patch injection
 */
public class IrisShaderBridge {
    // Iris Shader Programs
    public static final String PROGRAM_GBUFFERS_TERRAIN = "gbuffers_terrain";
    public static final String PROGRAM_GBUFFERS_WATER = "gbuffers_water";
    public static final String PROGRAM_SHADOW = "shadow";
    public static final String PROGRAM_DEFERRED = "deferred";

    // Standard Iris Uniform Names
    public static final String UNIFORM_SUN_VECTOR = "iris_SunVector";
    public static final String UNIFORM_SKY_COLOR = "iris_SkyColor";
    public static final String UNIFORM_CAMERA_POS = "iris_CameraPosition";
    public static final String UNIFORM_EYE_POSITION = "iris_EyePosition";
    public static final String UNIFORM_MODEL_VIEW = "iris_ModelViewMatrix";
    public static final String UNIFORM_PROJECTION = "iris_ProjectionMatrix";
    public static final String UNIFORM_NORMAL_MATRIX = "iris_NormalMatrix";

    // Standard Iris Texture Directives
    public static final String DIRECTIVE_DRAW_BUFFERS = "#pragma iris:drawbuffers";
    public static final String DIRECTIVE_RENDER_TARGETS = "#pragma iris:rendertargets";

    /**
     * Checks whether a shader source contains Iris-specific directives.
     */
    public static boolean containsIrisDirectives(String shaderSource) {
        if (shaderSource == null) return false;
        return shaderSource.contains("iris_") || shaderSource.contains("#pragma iris");
    }
}
