package me.ling.horizons2.neoforge.event;

import me.ling.horizons2.LingHorizons2;
import me.ling.horizons2.engine.WorldSectionManager;
import me.ling.horizons2.engine.ingest.ChunkIngestEngine;
import me.ling.horizons2.engine.renderer.LingMdiRenderer;
import me.ling.horizons2.neoforge.config.LingConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import me.ling.horizons2.engine.renderer.pipeline.IRenderPipeline;
import me.ling.horizons2.engine.renderer.pipeline.RenderContext;
import me.ling.horizons2.engine.renderer.pipeline.RenderPipelineManager;
import me.ling.horizons2.neoforge.gui.LingConfigScreen;
import me.ling.horizons2.neoforge.gui.LingKeyBindings;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.file.Path;

/**
 * NeoForge Client Event Handlers for LING Horizons 2.0.
 *
 * 1. Pushes fog distance to infinity to eliminate the vanilla fog wall.
 * 2. Hooks client chunk loading to ingest world geometry into the LOD octree.
 * 3. Hooks level rendering to execute GPU compute culling and Multi-Draw Indirect.
 */
@EventBusSubscriber(modid = LingHorizons2.MOD_ID, value = Dist.CLIENT)
public class LingClientEvents {
    private static float fogR = 0.7f;
    private static float fogG = 0.8f;
    private static float fogB = 1.0f;

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() == FogRenderer.FogMode.FOG_TERRAIN) {
            // Push fog plane to extreme distance so LODs are not shrouded by vanilla fog
            event.setNearPlaneDistance(999999.0f);
            event.setFarPlaneDistance(9999999.0f);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        fogR = event.getRed();
        fogG = event.getGreen();
        fogB = event.getBlue();
    }

    private static int ingestedChunksCount = 0;
    private static long lastLogTime = 0;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) {
            if (event.getChunk() instanceof LevelChunk chunk) {
                ChunkIngestEngine.getInstance().ingestChunk(chunk);
                ingestedChunksCount++;
                if (ingestedChunksCount % 50 == 0) {
                    LingHorizons2.LOGGER.info("[LING Horizons 2.0] Total chunks ingested: {}", ingestedChunksCount);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        Path baseDir = mc.gameDirectory.toPath().resolve("ling_horizons2_cache");
        WorldSectionManager.getInstance().initialize(baseDir);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WorldSectionManager.getInstance().clearWorld();
    }

    @SubscribeEvent
    public static void onBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        var pos = event.getPos();
        ChunkIngestEngine.getInstance().onBlockChanged(pos.getX(), pos.getY(), pos.getZ(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        var pos = event.getPos();
        ChunkIngestEngine.getInstance().onBlockChanged(pos.getX(), pos.getY(), pos.getZ(), null);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (LingKeyBindings.OPEN_CONFIG_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new LingConfigScreen(mc.screen));
        }
    }

    private static boolean debugHookInstalled = false;
    private static int debugErrorCount = 0;

    private static void ensureDebugHook() {
        if (debugHookInstalled) return;
        debugHookInstalled = true;
        try {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            org.lwjgl.opengl.GL43.glDebugMessageCallback((source, type, id, severity, length, message, userParam) -> {
                if (type == org.lwjgl.opengl.GL43.GL_DEBUG_TYPE_ERROR && severity == org.lwjgl.opengl.GL43.GL_DEBUG_SEVERITY_HIGH) {
                    if (debugErrorCount++ < 5) {
                        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                        StringBuilder sb = new StringBuilder();
                        sb.append("[LING DIAGNOSTIC] GL HIGH ERROR ID=").append(id).append("\n");
                        for (int i = 2; i < Math.min(stack.length, 30); i++) {
                            sb.append("    at ").append(stack[i].toString()).append("\n");
                        }
                        LingHorizons2.LOGGER.error("{}", sb.toString());
                    }
                }
            }, 0L);
            LingHorizons2.LOGGER.info("[LING Horizons 2.0] Synchronous GL Debug stack trace interceptor installed successfully.");
        } catch (Throwable t) {
            LingHorizons2.LOGGER.warn("[LING Horizons 2.0] Failed to install GL debug callback: {}", t.getMessage());
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        ensureDebugHook();

        boolean isOpaque = event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS;
        boolean isTranslucent = event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;

        if (!isOpaque && !isTranslucent) {
            return;
        }

        if (!LingConfig.CLIENT.enabled.get()) {
            return;
        }

        WorldSectionManager manager = WorldSectionManager.getInstance();
        if (!manager.isInitialized()) return;

        RenderPipelineManager pipelineManager = RenderPipelineManager.getInstance();
        if (!pipelineManager.isInitialized()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        Vector3f cameraPos = new Vector3f((float) camPos.x, (float) camPos.y, (float) camPos.z);

        if (isOpaque) {
            // Synchronize any updated meshes to GPU buffers once per frame before opaque pass
            manager.syncGpuBuffers(camPos.x, camPos.y, camPos.z);
        }

        var mainTarget = Minecraft.getInstance().getMainRenderTarget();
        int depthTexId = (mainTarget != null) ? mainTarget.getDepthTextureId() : 0;
        int screenW = (mainTarget != null) ? mainTarget.width : 1;
        int screenH = (mainTarget != null) ? mainTarget.height : 1;
        boolean useHiZ = LingConfig.CLIENT.enableHiZCulling.get();

        Matrix4f modelViewMatrix = event.getModelViewMatrix();
        Matrix4f projectionMatrix = event.getProjectionMatrix();
        Matrix4f viewProj = new Matrix4f(projectionMatrix).mul(modelViewMatrix);

        Vector4f[] planes = LingMdiRenderer.extractFrustumPlanes(viewProj);

        ClientLevel level = Minecraft.getInstance().level;
        Vector3f sunDir = new Vector3f(0.3f, 0.9f, 0.3f);
        if (level != null) {
            float sunAngle = level.getTimeOfDay(1.0f) * ((float) Math.PI * 2F);
            float skyX = -(float) Math.sin(sunAngle);
            float skyY = (float) Math.cos(sunAngle);
            sunDir.set(skyX, skyY, 0.2f).normalize();
        }

        float curvature = LingConfig.CLIENT.curvatureRadius.get().floatValue();
        float fogStart = 2000.0f;
        float fogEnd = 100000.0f;
        Vector4f fogColorVec = new Vector4f(fogR, fogG, fogB, 1.0f);

        RenderContext context = new RenderContext(
            viewProj,
            modelViewMatrix,
            projectionMatrix,
            cameraPos,
            curvature,
            fogColorVec,
            fogStart,
            fogEnd,
            sunDir,
            planes,
            depthTexId,
            screenW,
            screenH,
            useHiZ
        );

        if (isOpaque) {
            long now = System.currentTimeMillis();
            if (now - lastLogTime > 5000) {
                lastLogTime = now;
                IRenderPipeline active = pipelineManager.getActivePipeline();
                LingHorizons2.LOGGER.info("[LING Horizons 2.0] Pipeline: [{}] | Camera: ({}, {}, {}) | Hi-Z: {}",
                    active.getName(), (int) cameraPos.x, (int) cameraPos.y, (int) cameraPos.z, useHiZ ? "ON" : "OFF");
            }

            pipelineManager.renderOpaque(context);
        } else {
            pipelineManager.renderTranslucent(context);
        }
    }
}
