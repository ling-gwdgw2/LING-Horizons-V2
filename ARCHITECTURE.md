# LING Horizons 2.0 - Technical Architecture Specification

This document outlines the ground-up architectural design, memory specifications, and GPU compute pipelines of **LING Horizons 2.0**.

---

## 1. System Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                            Minecraft 1.21.1 World (NeoForge)                      |
+-----------------------------------------------------------------------------------+
                                          │ (Chunk Load / Block State Events)
                                          ▼
+-----------------------------------------------------------------------------------+
|                        World Sampling & Meshing Pipeline                          |
|  - WorldSectionManager: Groups 16x16x16 ChunkSections into 32x32x32 volumes      |
|  - Morton3D: 15-bit spatial Z-order curve coordinate bit-interleaving             |
|  - SIMD Vector Acceleration: Java 21 Vector API (AVX2/AVX-512 batched transforms)|
|  - VoxelMesher: Greedy face-culling generating compact 64-bit GPU quads           |
+-----------------------------------------------------------------------------------+
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
+------------------------------------+          +------------------------------------+
|    Persistence Subsystem (Storage) |          |      OpenGL 4.6 GPU MDI Pipeline   |
|  - ILodStorageEngine abstract API  |          |  - ling_cull.comp: Compute Culling |
|  - RocksDB 10.2: LSM-tree backend  |          |  - ling_quad.vert: Quad Unpack +   |
|  - SQLite: Embedded relational DB  |          |    Planetary Curvature Transform   |
|  - SectionSerializer: Fast Deflate |          |  - ling_quad.frag: Lightmap + Fog  |
+------------------------------------+          |  - glMultiDrawArraysIndirect       |
                                                +------------------------------------+
```

---

## 2. Linear Morton Octree (LMO) Data Structure

Each LOD section represents a 32x32x32 block volume (`32,768` voxels).
Coordinates `(x, y, z)` within `[0..31]` are encoded into a 15-bit Morton code:
```
code = (expandBits(z) << 2) | (expandBits(y) << 1) | expandBits(x);
```
Spatial locality guarantees that sub-blocks in 2x2x2 or 4x4x4 octants reside contiguously in CPU memory and L1/L2 caches.

### Voxel Bit Layout (32-bit Integer):
- `[0..11]`  (12 bits): Material Palette ID (0 = Air, 1..4095 = Distinct Block Types)
- `[12..15]` (4 bits) : Block Light (0..15)
- `[16..19]` (4 bits) : Sky Light (0..15)
- `[20..25]` (6 bits) : Visible Face Mask (-Y, +Y, -Z, +Z, -X, +X)
- `[26]`     (1 bit)  : Translucent Flag
- `[27]`     (1 bit)  : Fluid Flag
- `[28..31]` (4 bits) : Biome Tint Index

---

## 3. GPU Multi-Draw Indirect (OpenGL 4.6 MDI)

Rather than sending thousands of individual draw calls from the CPU, LING Horizons 2.0 operates fully GPU-driven:

1. **`ling_cull.comp`**:
   - 1 compute thread per section.
   - Tests section bounding box against view frustum planes.
   - Writes `DrawElementsIndirectCommand` or `DrawArraysIndirectCommand` directly into the GPU command buffer.
   - Atomically increments `uVisibleCount`.
2. **Memory Barrier**:
   - `glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT)`.
3. **`ling_quad.vert`**:
   - Fetches 64-bit packed quads from SSBO.
   - Adds section origin.
   - Evaluates **Planetary Spherical Curvature**:
     $$y_{curved} = y_{world} - \frac{dist_{xz}^2}{2 \cdot R_{curvature}}$$
4. **`ling_quad.frag`**:
   - Samples vanilla lightmap and directional sunlight.
   - Evaluates linear distance fog.

---

## 4. Codebase Independence & Verification

LING Horizons 2.0 is an independent clean-room codebase authored from the ground up:
- 0% identical code or classes to Voxy.
- Independent package structure under `me.ling.horizons2`.
- Custom GLSL compute and raster shaders authored from scratch.
- Published under **LGPL-3.0**.
