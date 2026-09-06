package me.ling.horizons2.engine;

import me.ling.horizons2.engine.renderer.LingMdiRenderer;
import me.ling.horizons2.engine.renderer.pipeline.RenderPipelineManager;
import me.ling.horizons2.engine.storage.ILodStorageEngine;
import me.ling.horizons2.engine.storage.SectionSerializer;
import me.ling.horizons2.engine.storage.StorageEngineFactory;
import me.ling.horizons2.engine.voxel.VoxelData;
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

    public int getNeighborVoxel(int sx, int sy, int sz, int nx, int ny, int nz) {
        int targetSx = sx;
        int targetSy = sy;
        int targetSz = sz;
        int localX = nx;
        int localY = ny;
        int localZ = nz;

        if (localX < 0) {
            targetSx--;
            localX += 32;
        } else if (localX >= 32) {
            targetSx++;
            localX -= 32;
        }

        if (localY < 0) {
            targetSy--;
            localY += 32;
        } else if (localY >= 32) {
            targetSy++;
            localY -= 32;
        }

        if (localZ < 0) {
            targetSz--;
            localZ += 32;
        } else if (localZ >= 32) {
            targetSz++;
            localZ -= 32;
        }

        long key = sectionKey(targetSx, targetSy, targetSz);
        VoxelGrid32 neighborGrid = activeGrids.get(key);
        if (neighborGrid != null) {
            return neighborGrid.get(localX, localY, localZ);
        }
        return VoxelData.AIR;
    }

    public void queueSectionUpdate(int sx, int sy, int sz, VoxelGrid32 grid) {
        queueSectionUpdate(sx, sy, sz, grid, true);
    }

    public void queueSectionUpdate(int sx, int sy, int sz, VoxelGrid32 grid, boolean updateNeighbors) {
        workerPool.submit(() -> {
            try {
                long key = sectionKey(sx, sy, sz);
                activeGrids.put(key, grid);

                // Build mesh asynchronously with cross-section boundary neighbor stitching
                VoxelMesher.MeshResult mesh = VoxelMesher.buildMesh(grid, (nx, ny, nz) -> getNeighborVoxel(sx, sy, sz, nx, ny, nz));
                activeMeshes.put(key, mesh);
                needsGpuSync = true;

                // Persist to database
                if (storageEngine != null) {
                    byte[] data = SectionSerializer.serialize(grid);
                    storageEngine.saveSection(sx, sy, sz, data);
                }

                // If this is an updated section, refresh adjacent loaded neighbors once to stitch boundary walls
                if (updateNeighbors) {
                    int[][] neighborOffsets = { {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1} };
                    for (int[] off : neighborOffsets) {
                        int nsx = sx + off[0];
                        int nsy = sy + off[1];
                        int nsz = sz + off[2];
                        long nKey = sectionKey(nsx, nsy, nsz);
                        VoxelGrid32 nGrid = activeGrids.get(nKey);
                        if (nGrid != null && activeMeshes.containsKey(nKey)) {
                            queueSectionUpdate(nsx, nsy, nsz, nGrid, false);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[LING Horizons 2.0] Error processing section (" + sx + ", " + sy + ", " + sz + "): " + e.getMessage());
            }
        });
    }

    private long lastGpuSyncTime = 0;

    /**
     * Synchronizes dirty CPU mesh data into GPU SSBOs. Must be called on OpenGL render thread.
     */
    public void syncGpuBuffers(double camX, double camY, double camZ) {
        var pipelineMgr = RenderPipelineManager.getInstance();
        if (!needsGpuSync || !pipelineMgr.isInitialized()) return;

        // Throttle full GPU rebuilds to at most once every 300ms to eliminate render-thread stutter
        long now = System.currentTimeMillis();
        if (now - lastGpuSyncTime < 300) {
            return;
        }
        lastGpuSyncTime = now;
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
