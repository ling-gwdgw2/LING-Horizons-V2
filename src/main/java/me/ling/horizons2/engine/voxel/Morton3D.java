package me.ling.horizons2.engine.voxel;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * High-performance 3D Morton Code (Z-Order Curve) encoder and decoder.
 * Provides spatial locality for 32x32x32 voxel volumes (5 bits per axis = 15-bit Morton code).
 * Includes scalar bitwise operations and Java 21 SIMD Vector acceleration.
 */
public final class Morton3D {
    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;
    public static final int GRID_SIZE = 32;
    public static final int TOTAL_VOXELS = GRID_SIZE * GRID_SIZE * GRID_SIZE; // 32,768
    public static final int MAX_CODE = TOTAL_VOXELS - 1;

    private Morton3D() {}

    /**
     * Interleaves the lower 5 bits of x, y, and z into a 15-bit Morton code.
     * Pattern: ... z2 y2 x2 z1 y1 x1 z0 y0 x0
     */
    public static int encode(int x, int y, int z) {
        return (expandBits5(z) << 2) | (expandBits5(y) << 1) | expandBits5(x);
    }

    /**
     * Extracts the X coordinate (0..31) from a 15-bit Morton code.
     */
    public static int decodeX(int code) {
        return compactBits5(code);
    }

    /**
     * Extracts the Y coordinate (0..31) from a 15-bit Morton code.
     */
    public static int decodeY(int code) {
        return compactBits5(code >> 1);
    }

    /**
     * Extracts the Z coordinate (0..31) from a 15-bit Morton code.
     */
    public static int decodeZ(int code) {
        return compactBits5(code >> 2);
    }

    /**
     * Expands 5 bits (0..31) so that there are 2 empty bits between each bit.
     * In:  ---- ---- ---- ---- ---- ---- ---4 3210
     * Out: ---- ---- ---- ---- ---4 --3- -2-- 1--0
     */
    private static int expandBits5(int v) {
        v &= 0x0000001F;
        v = (v | (v << 8)) & 0x0000100F;
        v = (v | (v << 4)) & 0x0000010C3;
        v = (v | (v << 2)) & 0x000009249;
        return v;
    }

    /**
     * Compacts every 3rd bit into a contiguous 5-bit integer.
     */
    private static int compactBits5(int v) {
        v &= 0x000009249;
        v = (v | (v >> 2)) & 0x0000010C3;
        v = (v | (v >> 4)) & 0x0000100F;
        v = (v | (v >> 8)) & 0x0000001F;
        return v;
    }

    /**
     * Encodes a batch of coordinates using Java 21 SIMD Vector instructions.
     */
    public static void batchEncode(int[] xCoords, int[] yCoords, int[] zCoords, int[] outCodes, int length) {
        int i = 0;
        int loopBound = SPECIES.loopBound(length);

        for (; i < loopBound; i += SPECIES.length()) {
            IntVector vx = IntVector.fromArray(SPECIES, xCoords, i);
            IntVector vy = IntVector.fromArray(SPECIES, yCoords, i);
            IntVector vz = IntVector.fromArray(SPECIES, zCoords, i);

            // Vectorized expansion
            IntVector expX = expandVector5(vx);
            IntVector expY = expandVector5(vy);
            IntVector expZ = expandVector5(vz);

            IntVector result = expX.or(expY.lanewise(VectorOperators.LSHL, 1))
                                  .or(expZ.lanewise(VectorOperators.LSHL, 2));
            result.intoArray(outCodes, i);
        }

        // Remainder scalar processing
        for (; i < length; i++) {
            outCodes[i] = encode(xCoords[i], yCoords[i], zCoords[i]);
        }
    }

    private static IntVector expandVector5(IntVector v) {
        v = v.and(0x0000001F);
        v = v.or(v.lanewise(VectorOperators.LSHL, 8)).and(0x0000100F);
        v = v.or(v.lanewise(VectorOperators.LSHL, 4)).and(0x0000010C3);
        v = v.or(v.lanewise(VectorOperators.LSHL, 2)).and(0x000009249);
        return v;
    }
}
