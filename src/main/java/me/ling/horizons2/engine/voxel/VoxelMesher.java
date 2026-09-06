package me.ling.horizons2.engine.voxel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * High-performance Face-Culling Mesher for LING Horizons 2.0.
 *
 * Traverses a VoxelGrid32 and generates compact 64-bit packed GPU quads.
 *
 * Quad 64-Bit Memory Layout:
 * [0..4]   (5 bits) : X local coordinate (0..31)
 * [5..9]   (5 bits) : Y local coordinate (0..31)
 * [10..14] (5 bits) : Z local coordinate (0..31)
 * [15..17] (3 bits) : Face Direction (0:-Y, 1:+Y, 2:-Z, 3:+Z, 4:-X, 5:+X)
 * [18..29] (12 bits): Material Palette ID (0..4095)
 * [30..33] (4 bits) : Block Light (0..15)
 * [34..37] (4 bits) : Sky Light (0..15)
 * [38..41] (4 bits) : Biome Tint Index (0..15)
 * [42]     (1 bit)  : Is Translucent
 * [43]     (1 bit)  : Is Fluid
 * [44..63] (20 bits): Reserved for LOD Level / AO Flags
 */
public class VoxelMesher {

    public static class MeshResult {
        public final long[] opaqueQuads;
        public final int opaqueCount;
        public final long[] translucentQuads;
        public final int translucentCount;

        public MeshResult(long[] opaqueQuads, int opaqueCount, long[] translucentQuads, int translucentCount) {
            this.opaqueQuads = opaqueQuads;
            this.opaqueCount = opaqueCount;
            this.translucentQuads = translucentQuads;
            this.translucentCount = translucentCount;
        }

        public boolean isEmpty() {
            return opaqueCount == 0 && translucentCount == 0;
        }
    }

    // Directional offsets for 6 faces: -Y, +Y, -Z, +Z, -X, +X
    private static final int[][] FACE_OFFSETS = {
        { 0, -1,  0}, // 0: Down (-Y)
        { 0,  1,  0}, // 1: Up (+Y)
        { 0,  0, -1}, // 2: North (-Z)
        { 0,  0,  1}, // 3: South (+Z)
        {-1,  0,  0}, // 4: West (-X)
        { 1,  0,  0}  // 5: East (+X)
    };

    /**
     * Builds a MeshResult containing packed quads for a given grid.
     */
    public static MeshResult buildMesh(VoxelGrid32 grid) {
        if (grid.isEmpty()) {
            return new MeshResult(new long[0], 0, new long[0], 0);
        }

        // Temporary buffers (max theoretical quads: 32x32x32 * 6 / 2)
        long[] opaqueTemp = new long[16384];
        long[] transTemp = new long[4096];
        int opaqueCount = 0;
        int transCount = 0;

        for (int y = 0; y < VoxelGrid32.SIZE; y++) {
            for (int z = 0; z < VoxelGrid32.SIZE; z++) {
                for (int x = 0; x < VoxelGrid32.SIZE; x++) {
                    int voxel = grid.get(x, y, z);
                    if (VoxelData.isAir(voxel)) continue;

                    int material = VoxelData.getMaterial(voxel);
                    boolean isTranslucent = VoxelData.isTranslucent(voxel);
                    boolean isFluid = VoxelData.isFluid(voxel);
                    int blockLight = VoxelData.getBlockLight(voxel);
                    int skyLight = VoxelData.getSkyLight(voxel);
                    int tint = VoxelData.getTint(voxel);

                    // Test all 6 neighboring faces
                    for (int face = 0; face < 6; face++) {
                        int nx = x + FACE_OFFSETS[face][0];
                        int ny = y + FACE_OFFSETS[face][1];
                        int nz = z + FACE_OFFSETS[face][2];

                        int neighbor = grid.get(nx, ny, nz);
                        boolean neighborIsAir = VoxelData.isAir(neighbor);
                        boolean neighborIsTranslucent = VoxelData.isTranslucent(neighbor);

                        // Quad is visible if neighbor is air, or if this is opaque and neighbor is translucent
                        boolean emitFace = neighborIsAir || (!isTranslucent && neighborIsTranslucent);

                        if (emitFace) {
                            long quad = packQuad(x, y, z, face, material, blockLight, skyLight, tint, isTranslucent, isFluid);

                            if (isTranslucent) {
                                if (transCount >= transTemp.length) {
                                    long[] expanded = new long[transTemp.length * 2];
                                    System.arraycopy(transTemp, 0, expanded, 0, transTemp.length);
                                    transTemp = expanded;
                                }
                                transTemp[transCount++] = quad;
                            } else {
                                if (opaqueCount >= opaqueTemp.length) {
                                    long[] expanded = new long[opaqueTemp.length * 2];
                                    System.arraycopy(opaqueTemp, 0, expanded, 0, opaqueTemp.length);
                                    opaqueTemp = expanded;
                                }
                                opaqueTemp[opaqueCount++] = quad;
                            }
                        }
                    }
                }
            }
        }

        long[] finalOpaque = new long[opaqueCount];
        System.arraycopy(opaqueTemp, 0, finalOpaque, 0, opaqueCount);

        long[] finalTrans = new long[transCount];
        System.arraycopy(transTemp, 0, finalTrans, 0, transCount);

        return new MeshResult(finalOpaque, opaqueCount, finalTrans, transCount);
    }

    public static long packQuad(int x, int y, int z, int face, int material, int blockLight, int skyLight, int tint, boolean translucent, boolean fluid) {
        long q = 0L;
        q |= (x & 0x1FL);
        q |= ((y & 0x1FL) << 5);
        q |= ((z & 0x1FL) << 10);
        q |= ((face & 0x7L) << 15);
        q |= ((material & 0xFFFL) << 18);
        q |= ((blockLight & 0xFL) << 30);
        q |= ((skyLight & 0xFL) << 34);
        q |= ((tint & 0xFL) << 38);
        if (translucent) q |= (1L << 42);
        if (fluid) q |= (1L << 43);
        return q;
    }
}
