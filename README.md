# LING Horizons 2.0

**LING Horizons 2.0** is an independent, next-generation, high-performance GPU-driven **Voxel Level-of-Detail (LOD)** rendering engine for **Minecraft 1.21.1 on NeoForge**.

Engineered **100% from the ground up**, LING Horizons 2.0 eliminates CPU rendering bottlenecks by combining **Linear Morton Octrees (LMO)**, **Java 21 SIMD Vector bit-packing**, **OpenGL 4.6 Multi-Draw Indirect (MDI)**, and real-time **Planetary Spherical Curvature**.

---

## Key Features

- **100% Ground-Up Architecture**: Built independently with zero legacy dependencies or third-party proprietary code.
- **Linear Morton Octree (LMO)**: Spatially-coherent 15-bit Morton Z-order curve indexing for 32x32x32 voxel sections.
- **Hardware SIMD Vector Acceleration**: Accelerated with `jdk.incubator.vector` for batched coordinate transforms and neighbor occlusion tests.
- **GPU-Driven OpenGL 4.6 MDI Pipeline**:
  - `ling_cull.comp`: High-speed compute shader evaluating view frustum culling directly on the GPU.
  - `ling_quad.vert` & `ling_quad.frag`: Vertex and fragment pipeline rendering packed 64-bit quads with zero CPU draw overhead.
- **Real-Time Planetary Curvature**: Authentic spherical Earth curvature transformation (`y -= distSq / (2 * R)`) calculated dynamically in vertex shading.
- **Multi-Engine Storage Architecture**: Pluggable high-performance world persistence supporting embedded **RocksDB 10.2** and **SQLite**.
- **In-Game Administration**: Built-in CLI commands (`/ling2`, `/ling2 status`, `/ling2 curvature <radius>`).

---

## Requirements

- **Minecraft**: 1.21.1
- **Mod Loader**: NeoForge 21.1.0+
- **Java**: Java 21 (JDK 21) with `--add-modules jdk.incubator.vector`
- **GPU**: OpenGL 4.6 compatible GPU (NVIDIA GTX 900+ / AMD GCN 2+ / Intel Arc)
- **Companion Mods**: Sodium 0.8+ / Iris 1.8+ (Recommended)

---

## Building from Source

```bash
git clone https://github.com/ling-gwdgw2/LING-Horizons-2.0.git
cd "LING Horizons 2.0"
./gradlew build
```

The output mod JAR will be located in: `build/libs/lingHorizons-V2-2.0.0.jar`.

---

## License

LING Horizons 2.0 is authored by **LING** and licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)**.
