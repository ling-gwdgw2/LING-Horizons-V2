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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
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

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) {
            if (event.getChunk() instanceof LevelChunk chunk) {
                ChunkIngestEngine.getInstance().ingestChunk(chunk);
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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }

        WorldSectionManager manager = WorldSectionManager.getInstance();
        if (!manager.isInitialized()) return;

        // Synchronize any updated meshes to GPU buffers
        manager.syncGpuBuffers();

        LingMdiRenderer renderer = manager.getRenderer();
        if (renderer == null || !renderer.isInitialized() || renderer.getTotalSections() == 0) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        Vector3f cameraPos = new Vector3f((float) camPos.x, (float) camPos.y, (float) camPos.z);

        Matrix4f modelViewMatrix = event.getModelViewMatrix();
        Matrix4f projectionMatrix = event.getProjectionMatrix();
        Matrix4f viewProj = new Matrix4f(projectionMatrix).mul(modelViewMatrix);

        Vector4f[] planes = LingMdiRenderer.extractFrustumPlanes(viewProj);

        if (!LingConfig.CLIENT.enabled.get()) {
            return;
        }

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

        renderer.render(viewProj, cameraPos, curvature, fogColorVec, fogStart, fogEnd, sunDir, planes);
    }
}
