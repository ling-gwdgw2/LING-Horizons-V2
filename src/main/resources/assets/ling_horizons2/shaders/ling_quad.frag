#version 460 core

in vec3 vWorldPos;
in vec3 vNormal;
in vec2 vUV;
in vec2 vLight;
flat in uint vMaterial;
flat in uint vTint;

layout(location = 0) out vec4 outColor;

uniform vec3 uCameraPos;
uniform vec4 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;
uniform vec3 uSunDirection;

// Material base color palette table
layout(std430, binding = 5) readonly buffer MaterialColorBuffer {
    vec4 uMaterialColors[];
};

void main() {
    // Base block color from material palette
    vec4 baseColor = uMaterialColors[vMaterial];
    if (baseColor.a < 0.05) discard;

    // Simple directional lighting
    float sunDot = max(dot(vNormal, normalize(uSunDirection)), 0.0);
    float diffuse = 0.4 + 0.6 * sunDot;

    // Lightmap simulation: combine block light and sky light
    float lightLevel = max(vLight.x * 0.9, vLight.y * 0.8 + 0.15);
    vec3 litColor = baseColor.rgb * diffuse * lightLevel;

    // Linear distance fog
    float dist = length(vWorldPos - uCameraPos);
    float fog = clamp((dist - uFogStart) / max(uFogEnd - uFogStart, 1.0), 0.0, 1.0);

    vec3 finalRgb = mix(litColor, uFogColor.rgb, fog);
    outColor = vec4(finalRgb, baseColor.a);
}
