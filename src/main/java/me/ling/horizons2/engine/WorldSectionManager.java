package me.ling.horizons2.engine;

import me.ling.horizons2.engine.renderer.LingMdiRenderer;
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
    private LingMdiRenderer renderer;
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

            renderer = new LingMdiRenderer();
            renderer.initialize();
            initialized = true;
        } catch (Exception e) {
            System.err.println("[LING Horizons 2.0] Storage/Renderer init warning: " + e.getMessage());
            if (renderer == null) {
                renderer = new LingMdiRenderer();
                renderer.initialize();
            }
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
    public void syncGpuBuffers() {
        if (!needsGpuSync || renderer == null || !renderer.isInitialized()) return;
        needsGpuSync = false;

        List<LingMdiRenderer.SectionRenderData> sectionList = new ArrayList<>(activeMeshes.size());
        for (Map.Entry<Long, VoxelMesher.MeshResult> entry : activeMeshes.entrySet()) {
            long key = entry.getKey();
            VoxelMesher.MeshResult mesh = entry.getValue();
            if (mesh != null && mesh.opaqueCount > 0) {
                int sx = unpackX(key);
                int sy = unpackY(key);
                int sz = unpackZ(key);
                sectionList.add(new LingMdiRenderer.SectionRenderData(sx, sy, sz, mesh.opaqueQuads, mesh.opaqueCount));
            }
        }
        renderer.uploadSectionData(sectionList);
        renderer.updateMaterialColors(VoxelPalette.getInstance());
    }

    public synchronized void clearWorld() {
        activeGrids.clear();
        activeMeshes.clear();
        if (renderer != null) {
            renderer.setTotalSections(0);
        }
        needsGpuSync = false;
    }

    public LingMdiRenderer getRenderer() {
        return renderer;
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
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        activeGrids.clear();
        activeMeshes.clear();
        initialized = false;
    }
}
