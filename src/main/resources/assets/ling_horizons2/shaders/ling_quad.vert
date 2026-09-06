#version 460 core

// 64-bit packed quad buffer
struct PackedQuad {
    uint low;  // [0..4] x, [5..9] y, [10..14] z, [15..17] face, [18..29] material, [30..33] blockLight
    uint high; // [34..37] skyLight, [38..41] tint, [42] translucent, [43] fluid
};

layout(std430, binding = 3) readonly buffer QuadBuffer {
    PackedQuad uQuads[];
};

// Section origin positions in world space
layout(std430, binding = 4) readonly buffer SectionOriginBuffer {
    vec4 uSectionOrigins[]; // xyz = world block pos, w = unused
};

uniform mat4 uViewProjMatrix;
uniform vec3 uCameraPos;
uniform float uCurvatureRadius; // 0.0 = Flat, > 0.0 = Spherical planet radius

out vec3 vWorldPos;
out vec3 vNormal;
out vec2 vUV;
out vec2 vLight;
flat out uint vMaterial;
flat out uint vTint;

// Unit normals for 6 faces: -Y, +Y, -Z, +Z, -X, +X
const vec3 FACE_NORMALS[6] = vec3[6](
    vec3( 0.0, -1.0,  0.0),
    vec3( 0.0,  1.0,  0.0),
    vec3( 0.0,  0.0, -1.0),
    vec3( 0.0,  0.0,  1.0),
    vec3(-1.0,  0.0,  0.0),
    vec3( 1.0,  0.0,  0.0)
);

// 4 corner vertex offsets for each face
const vec3 QUAD_CORNERS[6][4] = vec3[6][4](
    // 0: Down (-Y)
    vec3[4](vec3(0,0,1), vec3(0,0,0), vec3(1,0,0), vec3(1,0,1)),
    // 1: Up (+Y)
    vec3[4](vec3(0,1,0), vec3(0,1,1), vec3(1,1,1), vec3(1,1,0)),
    // 2: North (-Z)
    vec3[4](vec3(1,0,0), vec3(0,0,0), vec3(0,1,0), vec3(1,1,0)),
    // 3: South (+Z)
    vec3[4](vec3(0,0,1), vec3(1,0,1), vec3(1,1,1), vec3(0,1,1)),
    // 4: West (-X)
    vec3[4](vec3(0,0,0), vec3(0,0,1), vec3(0,1,1), vec3(0,1,0)),
    // 5: East (+X)
    vec3[4](vec3(1,0,1), vec3(1,0,0), vec3(1,1,0), vec3(1,1,1))
);

const vec2 CORNER_UVS[4] = vec2[4](
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0)
);

// 6 vertices per quad forming 2 triangles: (0, 1, 2) and (0, 2, 3)
const uint CORNER_INDICES[6] = uint[6](0u, 1u, 2u, 0u, 2u, 3u);

void main() {
    uint quadIndex = gl_VertexID / 6u;
    uint cornerIndex = CORNER_INDICES[gl_VertexID % 6u];

    PackedQuad q = uQuads[quadIndex];

    // Decode low uint
    uint lx = q.low & 0x1Fu;
    uint ly = (q.low >> 5u) & 0x1Fu;
    uint lz = (q.low >> 10u) & 0x1Fu;
    uint face = (q.low >> 15u) & 0x7u;
    uint mat = (q.low >> 18u) & 0xFFFu;
    uint blockLight = (q.low >> 30u) & 0x3u; // lower 2 bits in low
    blockLight |= (q.high & 0x3u) << 2u;     // upper 2 bits in high

    // Decode high uint
    uint skyLight = (q.high >> 2u) & 0xFu;
    uint tint = (q.high >> 6u) & 0xFu;

    // Corner offset
    vec3 cornerOffset = QUAD_CORNERS[face][cornerIndex];
    vec3 localPos = vec3(lx, ly, lz) + cornerOffset;

    // Add Section World Origin (from Draw Command baseInstance)
    vec3 secOrigin = uSectionOrigins[gl_BaseInstance].xyz;
    vec3 worldPos = secOrigin + localPos;

    // Camera-relative position for float precision
    vec3 relPos = worldPos - uCameraPos;

    // Apply Geodesic Planetary Curvature
    if (uCurvatureRadius > 0.0) {
        float distSq = dot(relPos.xz, relPos.xz);
        relPos.y -= distSq / (2.0 * uCurvatureRadius);
        worldPos.y -= distSq / (2.0 * uCurvatureRadius);
    }

    vWorldPos = worldPos;
    vNormal = FACE_NORMALS[face];
    vUV = CORNER_UVS[cornerIndex];
    vLight = vec2(float(blockLight) / 15.0, float(skyLight) / 15.0);
    vMaterial = mat;
    vTint = tint;

    gl_Position = uViewProjMatrix * vec4(relPos, 1.0);
}
