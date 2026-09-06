package me.ling.horizons2.engine;

import me.ling.horizons2.engine.renderer.LingMdiRenderer;
import me.ling.horizons2.engine.renderer.pipeline.RenderPipelineManager;
import me.ling.horizons2.engine.storage.ILodStorageEngine;
import me.ling.horizons2.engine.storage.SectionSerializer;
import me.ling.horizons2.engine.storage.StorageEngineFactory;
import me.ling.horizons2.engine.voxel.VoxelGrid32;
import me.ling.horizons2.engine.voxel.VoxelMesher;
import me.ling.horizons2.engine.voxel.VoxelPalette;
import me.ling.horizons2.neoforge.config.LingConfig;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages active LOD world sections, persistence, and GPU buffer updates.
 */
public class WorldSectionManager implements AutoCloseable {
    private static final WorldSectionManager INSTANCE = new WorldSectionManager();

    public static WorldSectionManager getInstance() {
        return INSTANCE;
    }

    private final ConcurrentHashMap<Long, VoxelGrid32> activeGrids = new ConcurrentHashMap<>(512);
    private final ConcurrentHashMap<Long, VoxelMesher.MeshResult> activeMeshes = new ConcurrentHashMap<>(512);

    private final ExecutorService workerPool = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
        r -> {
            Thread t = new Thread(r, "LING-Horizons-Worker");
            t.setDaemon(true);
            return t;
        }
    );

    private ILodStorageEngine storageEngine;
    private volatile boolean initialized = false;
    private volatile boolean needsGpuSync = false;

    public synchronized void initialize(Path worldSaveDir) {
        if (initialized) return;

        try {
            if (worldSaveDir == null) {
                worldSaveDir = Minecraft.getInstance().gameDirectory.toPath().resolve("ling_horizons2_cache");
            }

            var engineType = LingConfig.CLIENT.storageEngine.get();
            storageEngine = StorageEngineFactory.createEngine(engineType);
            storageEngine.initialize(worldSaveDir.resolve("ling_horizons2"));

            RenderPipelineManager.getInstance().initialize();
            initialized = true;
        } catch (Exception e) {
            System.err.println("[LING Horizons 2.0] Storage/Renderer init warning: " + e.getMessage());
            RenderPipelineManager.getInstance().initialize();
            initialized = true;
        }
    }

    public static long sectionKey(int x, int y, int z) {
        return (((long) x & 0x3FFFFF) << 42) | (((long) y & 0xFFFFF) << 22) | ((long) z & 0x3FFFFF);
    }

    public static int unpackX(long key) {
        return (int) (key >> 42);
    }

    public static int unpackY(long key) {
        return (int) (key << 22 >> 44);
    }

    public static int unpackZ(long key) {
        return (int) (key << 42 >> 42);
    }

    public VoxelGrid32 getOrCreateGrid(int sx, int sy, int sz) {
        long key = sectionKey(sx, sy, sz);
        return activeGrids.computeIfAbsent(key, k -> {
            if (storageEngine != null) {
                try {
                    byte[] data = storageEngine.loadSection(sx, sy, sz);
                    if (data != null && data.length > 0) {
                        return SectionSerializer.deserialize(data);
                    }
                } catch (Exception ignored) {}
            }
            return new VoxelGrid32();
        });
    }

    public void queueSectionUpdate(int sx, int sy, int sz, VoxelGrid32 grid) {
        workerPool.submit(() -> {
            try {
                long key = sectionKey(sx, sy, sz);
                activeGrids.put(key, grid);

                // Build mesh asynchronously
                VoxelMesher.MeshResult mesh = VoxelMesher.buildMesh(grid);
                activeMeshes.put(key, mesh);
                needsGpuSync = true;

                // Persist to database
                if (storageEngine != null) {
                    byte[] data = SectionSerializer.serialize(grid);
                    storageEngine.saveSection(sx, sy, sz, data);
                }
            } catch (Exception e) {
                System.err.println("[LING Horizons 2.0] Error processing section (" + sx + ", " + sy + ", " + sz + "): " + e.getMessage());
            }
        });
    }

    /**
     * Synchronizes dirty CPU mesh data into GPU SSBOs. Must be called on OpenGL render thread.
     */
    public void syncGpuBuffers(double camX, double camY, double camZ) {
        var pipelineMgr = RenderPipelineManager.getInstance();
        if (!needsGpuSync || !pipelineMgr.isInitialized()) return;
        needsGpuSync = false;

        List<LingMdiRenderer.SectionRenderData> opaqueList = new ArrayList<>(activeMeshes.size());
        List<LingMdiRenderer.SectionRenderData> translucentList = new ArrayList<>(activeMeshes.size() / 2);

        for (Map.Entry<Long, VoxelMesher.MeshResult> entry : activeMeshes.entrySet()) {
            long key = entry.getKey();
            VoxelMesher.MeshResult mesh = entry.getValue();
            if (mesh != null) {
                int sx = unpackX(key);
                int sy = unpackY(key);
                int sz = unpackZ(key);

                if (mesh.opaqueCount > 0) {
                    opaqueList.add(new LingMdiRenderer.SectionRenderData(sx, sy, sz, mesh.opaqueQuads, mesh.opaqueCount));
                }
                if (mesh.translucentCount > 0) {
                    translucentList.add(new LingMdiRenderer.SectionRenderData(sx, sy, sz, mesh.translucentQuads, mesh.translucentCount));
                }
            }
        }

        // Sort translucent sections back-to-front for proper alpha blending
        translucentList.sort((a, b) -> {
            double da = (a.sx * 32.0 + 16.0 - camX) * (a.sx * 32.0 + 16.0 - camX)
                      + (a.sy * 32.0 + 16.0 - camY) * (a.sy * 32.0 + 16.0 - camY)
                      + (a.sz * 32.0 + 16.0 - camZ) * (a.sz * 32.0 + 16.0 - camZ);
            double db = (b.sx * 32.0 + 16.0 - camX) * (b.sx * 32.0 + 16.0 - camX)
                      + (b.sy * 32.0 + 16.0 - camY) * (b.sy * 32.0 + 16.0 - camY)
                      + (b.sz * 32.0 + 16.0 - camZ) * (b.sz * 32.0 + 16.0 - camZ);
            return Double.compare(db, da);
        });

        pipelineMgr.uploadSectionData(opaqueList, translucentList);
        if (pipelineMgr.getVanillaPipeline() != null && pipelineMgr.getVanillaPipeline().isReady()) {
            pipelineMgr.getVanillaPipeline().getMdiRenderer().updateMaterialColors(VoxelPalette.getInstance());
        }
        if (pipelineMgr.getIrisPipeline() != null && pipelineMgr.getIrisPipeline().isReady()) {
            pipelineMgr.getIrisPipeline().getMdiRenderer().updateMaterialColors(VoxelPalette.getInstance());
        }
    }

    public synchronized void clearWorld() {
        activeGrids.clear();
        activeMeshes.clear();
        RenderPipelineManager.getInstance().clear();
        needsGpuSync = false;
    }

    public LingMdiRenderer getRenderer() {
        var vp = RenderPipelineManager.getInstance().getVanillaPipeline();
        return (vp != null) ? vp.getMdiRenderer() : null;
    }

    public RenderPipelineManager getPipelineManager() {
        return RenderPipelineManager.getInstance();
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public synchronized void close() {
        workerPool.shutdown();
        if (storageEngine != null) {
            try {
                storageEngine.close();
            } catch (Exception ignored) {}
            storageEngine = null;
        }
        RenderPipelineManager.getInstance().close();
        activeGrids.clear();
        activeMeshes.clear();
        initialized = false;
    }
}
