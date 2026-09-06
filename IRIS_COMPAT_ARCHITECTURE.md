# LING Horizons 2.0: Modular Rendering & Iris Decoupling Architecture

## Overview
To facilitate independent, crash-proof future development, the rendering subsystem of **LING Horizons 2.0** is divided into decoupled, modular pipelines governed by a common abstraction layer.

This guarantees that:
1. **The core mod never crashes if Iris is missing** (zero `NoClassDefFoundError` risk).
2. **Developers can work on Iris shaderpack features** without risking breaking the stable Vanilla/Sodium engine.
3. **The engine automatically switches pipelines at runtime** when players toggle shaderpacks in-game.

---

## Architecture Diagram

```
me.ling.horizons2.engine.renderer/
│
├── pipeline/                              <-- Core Pipeline Abstraction
│   ├── IRenderPipeline.java              (Contract interface for all rendering engines)
│   ├── RenderContext.java                (DTO encapsulating frame camera, matrices, lights)
│   └── RenderPipelineManager.java        (Dynamic pipeline selector and geometry dispatcher)
│
├── vanilla/                              <-- Pure Vanilla / Sodium Pipeline
│   ├── VanillaRenderPipeline.java        (Default MDI renderer using standalone shaders)
│   ├── LingMdiRenderer.java             (GPU Multi-Draw Indirect buffers & execution)
│   └── LingHiZBuffer.java               (Hierarchical-Z GPU compute depth downsampling)
│
└── iris/                                 <-- Isolated Iris Shaderpack Pipeline
    ├── IrisCompatHelper.java             (Crash-proof reflection/lazy loader for IrisApi)
    ├── IrisRenderPipeline.java           (Dedicated pipeline for shaderpacks)
    └── IrisShaderBridge.java             (Symbolic G-buffer directives & uniform contracts)
```

---

## Detailed Component Specifications

### 1. `pipeline/` (Shared Abstraction Layer)
- **`IRenderPipeline`**:
  Defines the mandatory lifecycle methods (`initialize`, `renderOpaque`, `renderTranslucent`, `uploadSectionData`, `clear`, `close`). Both Vanilla and Iris pipelines implement this exact interface.
- **`RenderContext`**:
  Encapsulates frame-level render data (View-Projection matrix, camera vector, fog range, sun angle, frustum clipping planes, and Hi-Z state). Renderers never depend directly on NeoForge or Minecraft event objects.
- **`RenderPipelineManager`**:
  Maintains references to both pipelines.
  - When Iris is loaded and an active shaderpack is detected via `IrisCompatHelper.isShaderpackActive()`, it dynamically routes calls to `IrisRenderPipeline`.
  - When shaders are disabled (or Iris is not installed), it seamlessly routes calls to `VanillaRenderPipeline`.
  - Automatically synchronizes mesh uploads to both pipelines so pressing `K` (toggle shader) in-game switches instantly without chunk reload delay.

### 2. `vanilla/` (Standard Pipeline)
- **`VanillaRenderPipeline`**:
  Wraps `LingMdiRenderer` and custom OpenGL 4.6 shaders (`ling_quad.vert`, `ling_quad.frag`, `ling_cull.comp`).
  - Contains **0% Iris dependencies**.
  - Guaranteed to work in vanilla Minecraft, standard NeoForge, and with Sodium.

### 3. `iris/` (Shaderpack Pipeline)
- **`IrisCompatHelper`**:
  Uses an isolated nested static class (`IrisApiHolder`) that is only loaded if `ModList.get().isLoaded("iris")` returns `true`. This prevents JVM classlink errors when playing without Iris.
- **`IrisRenderPipeline`**:
  Dedicated entry point for shaderpack rendering.
- **`IrisShaderBridge`**:
  Defines symbolic constants for Iris draw buffers (`colortex0` to `colortex15`, `depthtex0`, etc.) and Iris uniforms (`iris_SunVector`, `iris_CameraPosition`, etc.).

---

## Guide for Future Developers: Adding Iris Shaderpack Features

When developing advanced Iris features (such as custom G-buffer attachments or shaderpack AST patching):

1. **Only modify files inside `me.ling.horizons2.engine.renderer.iris`**.
2. **Never import `net.irisshaders.iris.*` in `vanilla` or `pipeline` packages**.
3. In `IrisRenderPipeline.java`:
   - Bind custom framebuffer attachments matching the active shaderpack's `gbuffers_terrain` / `gbuffers_water` directives.
   - Pass Iris uniform values using `IrisShaderBridge`.
4. If an unrecoverable shaderpack error occurs, the pipeline can safely delegate back to `VanillaRenderPipeline` as a fallback.
