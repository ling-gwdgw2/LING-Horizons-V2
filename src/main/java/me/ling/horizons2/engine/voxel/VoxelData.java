package me.ling.horizons2.engine.voxel;

/**
 * Packed 32-bit Voxel Representation for LING Horizons 2.0.
 *
 * Bit Layout:
 * [0..11]   (12 bits): Material Palette ID (0 = Air, 1..4095 = Distinct Block Types)
 * [12..15]  (4 bits) : Block Light Level (0..15)
 * [16..19]  (4 bits) : Sky Light Level (0..15)
 * [20..25]  (6 bits) : Visible Face Mask (0: -Y, 1: +Y, 2: -Z, 3: +Z, 4: -X, 5: +X)
 * [26]      (1 bit)  : Is Translucent (Water, Stained Glass)
 * [27]      (1 bit)  : Is Fluid
 * [28..31]  (4 bits) : Biome Color Quantized Tint (0..15)
 */
public final class VoxelData {
    public static final int AIR = 0;

    public static final int MASK_MATERIAL    = 0x00000FFF; // 12 bits
    public static final int SHIFT_MATERIAL   = 0;

    public static final int MASK_BLOCK_LIGHT = 0x0000000F; // 4 bits
    public static final int SHIFT_BLOCK_LIGHT= 12;

    public static final int MASK_SKY_LIGHT   = 0x0000000F; // 4 bits
    public static final int SHIFT_SKY_LIGHT  = 16;

    public static final int MASK_FACES       = 0x0000003F; // 6 bits
    public static final int SHIFT_FACES      = 20;

    public static final int FLAG_TRANSLUCENT = 1 << 26;
    public static final int FLAG_FLUID       = 1 << 27;

    public static final int MASK_TINT        = 0x0000000F; // 4 bits
    public static final int SHIFT_TINT       = 28;

    // Face directional constants
    public static final int FACE_DOWN  = 1 << 0; // -Y
    public static final int FACE_UP    = 1 << 1; // +Y
    public static final int FACE_NORTH = 1 << 2; // -Z
    public static final int FACE_SOUTH = 1 << 3; // +Z
    public static final int FACE_WEST  = 1 << 4; // -X
    public static final int FACE_EAST  = 1 << 5; // +X
    public static final int ALL_FACES  = 0x3F;

    private VoxelData() {}

    /**
     * Packs voxel components into a compact 32-bit integer.
     */
    public static int pack(int materialId, int blockLight, int skyLight, int faceMask, boolean translucent, boolean fluid, int tint) {
        int packed = (materialId & MASK_MATERIAL);
        packed |= ((blockLight & MASK_BLOCK_LIGHT) << SHIFT_BLOCK_LIGHT);
        packed |= ((skyLight & MASK_SKY_LIGHT) << SHIFT_SKY_LIGHT);
        packed |= ((faceMask & MASK_FACES) << SHIFT_FACES);
        if (translucent) packed |= FLAG_TRANSLUCENT;
        if (fluid) packed |= FLAG_FLUID;
        packed |= ((tint & MASK_TINT) << SHIFT_TINT);
        return packed;
    }

    public static int getMaterial(int voxel) {
        return voxel & MASK_MATERIAL;
    }

    public static int getBlockLight(int voxel) {
        return (voxel >>> SHIFT_BLOCK_LIGHT) & MASK_BLOCK_LIGHT;
    }

    public static int getSkyLight(int voxel) {
        return (voxel >>> SHIFT_SKY_LIGHT) & MASK_SKY_LIGHT;
    }

    public static int getFaceMask(int voxel) {
        return (voxel >>> SHIFT_FACES) & MASK_FACES;
    }

    public static boolean isTranslucent(int voxel) {
        return (voxel & FLAG_TRANSLUCENT) != 0;
    }

    public static boolean isFluid(int voxel) {
        return (voxel & FLAG_FLUID) != 0;
    }

    public static int getTint(int voxel) {
        return (voxel >>> SHIFT_TINT) & MASK_TINT;
    }

    public static boolean isAir(int voxel) {
        return (voxel & MASK_MATERIAL) == 0;
    }

    public static int withFaceMask(int voxel, int faceMask) {
        return (voxel & ~(MASK_FACES << SHIFT_FACES)) | ((faceMask & MASK_FACES) << SHIFT_FACES);
    }
}
