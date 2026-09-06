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

    // Directional sunlight
    vec3 sunDir = length(uSunDirection) > 0.01 ? normalize(uSunDirection) : vec3(0.3, 0.9, 0.3);
    float sunDot = max(dot(vNormal, sunDir), 0.0);
    float diffuse = 0.5 + 0.5 * sunDot;

    // Lightmap simulation: combine block light and sky light with ambient minimum
    float lightLevel = max(max(vLight.x * 0.9, vLight.y * 0.85), 0.25);
    vec3 litColor = baseColor.rgb * diffuse * lightLevel;

    // Linear distance fog
    float dist = length(vWorldPos - uCameraPos);
    float fogSpan = max(uFogEnd - uFogStart, 1.0);
    float fog = clamp((dist - uFogStart) / fogSpan, 0.0, 1.0);

    vec3 finalRgb = mix(litColor, uFogColor.rgb, fog);
    outColor = vec4(finalRgb, baseColor.a);
}
