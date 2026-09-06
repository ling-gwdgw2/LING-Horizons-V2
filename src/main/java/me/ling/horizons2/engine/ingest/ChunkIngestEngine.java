package me.ling.horizons2.engine.ingest;

import me.ling.horizons2.engine.WorldSectionManager;
import me.ling.horizons2.engine.voxel.VoxelData;
import me.ling.horizons2.engine.voxel.VoxelGrid32;
import me.ling.horizons2.engine.voxel.VoxelPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Ingests Minecraft 16x16x16 chunk sections into LING Horizons 32x32x32 LOD grid octants.
 */
public class ChunkIngestEngine {
    private static final ChunkIngestEngine INSTANCE = new ChunkIngestEngine();

    public static ChunkIngestEngine getInstance() {
        return INSTANCE;
    }

    private ChunkIngestEngine() {}

    public void ingestChunk(LevelChunk chunk) {
        if (chunk == null) return;

        WorldSectionManager sectionManager = WorldSectionManager.getInstance();
        if (!sectionManager.isInitialized()) {
            Minecraft mc = Minecraft.getInstance();
            Path baseDir = mc.gameDirectory != null ? mc.gameDirectory.toPath().resolve("ling_horizons2_cache") : Path.of("ling_horizons2_cache");
            sectionManager.initialize(baseDir);
        }

        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        int sx = Math.floorDiv(cx, 2);
        int sz = Math.floorDiv(cz, 2);
        int ox = Math.floorMod(cx, 2) * 16;
        int oz = Math.floorMod(cz, 2) * 16;

        VoxelPalette palette = VoxelPalette.getInstance();
        LevelChunkSection[] sections = chunk.getSections();
        Set<Long> affectedSections = new HashSet<>();

        for (int secIdx = 0; secIdx < sections.length; secIdx++) {
            LevelChunkSection section = sections[secIdx];
            if (section == null || section.hasOnlyAir()) continue;

            int sectionY = chunk.getSectionYFromSectionIndex(secIdx);
            int sy = Math.floorDiv(sectionY, 2);
            int oy = Math.floorMod(sectionY, 2) * 16;

            VoxelGrid32 grid = sectionManager.getOrCreateGrid(sx, sy, sz);
            boolean hasBlocks = false;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;

                        int matId = palette.getOrCreateId(state);
                        boolean translucent = palette.isTranslucent(matId);
                        boolean fluid = palette.isFluid(matId);

                        int voxel = VoxelData.pack(matId, 0, 15, 0x3F, translucent, fluid, 0);
                        grid.set(ox + x, oy + y, oz + z, voxel);
                        hasBlocks = true;
                    }
                }
            }

            if (hasBlocks) {
                affectedSections.add(WorldSectionManager.sectionKey(sx, sy, sz));
            }
        }

        // Queue dirty sections for asynchronous meshing and persistence
        for (long key : affectedSections) {
            int ssx = WorldSectionManager.unpackX(key);
            int ssy = WorldSectionManager.unpackY(key);
            int ssz = WorldSectionManager.unpackZ(key);
            VoxelGrid32 grid = sectionManager.getOrCreateGrid(ssx, ssy, ssz);
            sectionManager.queueSectionUpdate(ssx, ssy, ssz, grid);
        }
    }
}
