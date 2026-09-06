package me.ling.horizons2.engine.voxel;

import java.util.Arrays;

/**
 * 32x32x32 Voxel Volume Container for LING Horizons 2.0.
 *
 * Utilizes a sparse internal representation:
 * 1. Air Section: 0 bytes array overhead.
 * 2. Uniform Section (e.g. solid deepslate/stone): Single-value storage without array allocation.
 * 3. Heterogeneous Section: Full Morton-ordered 32K voxel array.
 */
public class VoxelGrid32 {
    public static final int SIZE = Morton3D.GRID_SIZE;
    public static final int VOLUME = Morton3D.TOTAL_VOXELS;

    private int[] voxels; // Null if empty or uniform
    private int uniformVoxel = VoxelData.AIR;
    private boolean isUniform = true;
    private int nonAirCount = 0;
    private int translucentCount = 0;

    public VoxelGrid32() {
        this.isUniform = true;
        this.uniformVoxel = VoxelData.AIR;
    }

    public VoxelGrid32(int uniformValue) {
        this.isUniform = true;
        this.uniformVoxel = uniformValue;
        if (!VoxelData.isAir(uniformValue)) {
            this.nonAirCount = VOLUME;
            if (VoxelData.isTranslucent(uniformValue)) {
                this.translucentCount = VOLUME;
            }
        }
    }

    private void expandIfUniform() {
        if (!isUniform) return;
        voxels = new int[VOLUME];
        if (uniformVoxel != VoxelData.AIR) {
            Arrays.fill(voxels, uniformVoxel);
        }
        isUniform = false;
    }

    public void set(int x, int y, int z, int voxel) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            return;
        }

        int index = Morton3D.encode(x, y, z);
        if (isUniform) {
            if (uniformVoxel == voxel) return;
            expandIfUniform();
        }

        int old = voxels[index];
        if (old == voxel) return;

        voxels[index] = voxel;

        if (VoxelData.isAir(old) && !VoxelData.isAir(voxel)) {
            nonAirCount++;
        } else if (!VoxelData.isAir(old) && VoxelData.isAir(voxel)) {
            nonAirCount--;
        }

        if (VoxelData.isTranslucent(old)) translucentCount--;
        if (VoxelData.isTranslucent(voxel)) translucentCount++;
    }

    public int get(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            return VoxelData.AIR;
        }
        if (isUniform) return uniformVoxel;
        return voxels[Morton3D.encode(x, y, z)];
    }

    public int getByIndex(int mortonIndex) {
        if (isUniform) return uniformVoxel;
        return voxels[mortonIndex];
    }

    public boolean isEmpty() {
        return nonAirCount == 0 && (isUniform ? uniformVoxel == VoxelData.AIR : true);
    }

    public boolean isUniform() {
        return isUniform;
    }

    public int getUniformVoxel() {
        return uniformVoxel;
    }

    public int getNonAirCount() {
        return nonAirCount;
    }

    public boolean hasTranslucents() {
        return translucentCount > 0;
    }

    public int[] getRawData() {
        if (isUniform) {
            expandIfUniform();
        }
        return voxels;
    }
}
