package me.ling.horizons2.engine.voxel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VoxelEngineTest {

    @Test
    public void testMortonEncodingDecoding() {
        for (int x = 0; x < 32; x += 7) {
            for (int y = 0; y < 32; y += 7) {
                for (int z = 0; z < 32; z += 7) {
                    int code = Morton3D.encode(x, y, z);
                    assertEquals(x, Morton3D.decodeX(code), "X mismatch");
                    assertEquals(y, Morton3D.decodeY(code), "Y mismatch");
                    assertEquals(z, Morton3D.decodeZ(code), "Z mismatch");
                }
            }
        }
    }

    @Test
    public void testVoxelDataPacking() {
        int material = 1234;
        int blockLight = 14;
        int skyLight = 15;
        int faceMask = VoxelData.FACE_UP | VoxelData.FACE_NORTH;
        boolean translucent = true;
        boolean fluid = false;
        int tint = 5;

        int packed = VoxelData.pack(material, blockLight, skyLight, faceMask, translucent, fluid, tint);

        assertEquals(material, VoxelData.getMaterial(packed));
        assertEquals(blockLight, VoxelData.getBlockLight(packed));
        assertEquals(skyLight, VoxelData.getSkyLight(packed));
        assertEquals(faceMask, VoxelData.getFaceMask(packed));
        assertTrue(VoxelData.isTranslucent(packed));
        assertFalse(VoxelData.isFluid(packed));
        assertEquals(tint, VoxelData.getTint(packed));
        assertFalse(VoxelData.isAir(packed));
    }

    @Test
    public void testVoxelGridAndMesher() {
        VoxelGrid32 grid = new VoxelGrid32();
        assertTrue(grid.isEmpty());

        // Place a single solid block at (10, 10, 10)
        int solidVoxel = VoxelData.pack(1, 0, 15, VoxelData.ALL_FACES, false, false, 0);
        grid.set(10, 10, 10, solidVoxel);

        assertFalse(grid.isEmpty());
        assertEquals(solidVoxel, grid.get(10, 10, 10));

        // A single isolated block should have all 6 outer faces exposed
        VoxelMesher.MeshResult mesh = VoxelMesher.buildMesh(grid);
        assertEquals(6, mesh.opaqueCount);
        assertEquals(0, mesh.translucentCount);
    }
}
