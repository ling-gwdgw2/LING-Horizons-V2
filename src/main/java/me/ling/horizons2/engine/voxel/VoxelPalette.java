package me.ling.horizons2.engine.voxel;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe Dynamic Material Palette for LING Horizons 2.0.
 * Maps Minecraft BlockStates to compact 12-bit Material IDs (0..4095).
 */
public class VoxelPalette {
    public static final int MAX_MATERIALS = 4096;
    public static final int AIR_ID = 0;

    private static final VoxelPalette INSTANCE = new VoxelPalette();

    public static VoxelPalette getInstance() {
        return INSTANCE;
    }

    private final ConcurrentHashMap<BlockState, Integer> stateToId = new ConcurrentHashMap<>(1024);
    private final BlockState[] idToState = new BlockState[MAX_MATERIALS];
    private final byte[] renderTypeFlags = new byte[MAX_MATERIALS]; // 0: Opaque, 1: Translucent, 2: Fluid
    private final AtomicInteger nextId = new AtomicInteger(1);

    public VoxelPalette() {
        // ID 0 is strictly Air
        stateToId.put(Blocks.AIR.defaultBlockState(), AIR_ID);
        stateToId.put(Blocks.CAVE_AIR.defaultBlockState(), AIR_ID);
        stateToId.put(Blocks.VOID_AIR.defaultBlockState(), AIR_ID);
        idToState[AIR_ID] = Blocks.AIR.defaultBlockState();
        renderTypeFlags[AIR_ID] = 0;
    }

    public int getOrCreateId(BlockState state) {
        if (state == null || state.isAir()) {
            return AIR_ID;
        }

        Integer existing = stateToId.get(state);
        if (existing != null) {
            return existing;
        }

        int id = nextId.getAndIncrement();
        if (id >= MAX_MATERIALS) {
            // Saturated palette fallback: map to basic stone or bedrock
            return 1;
        }

        idToState[id] = state;
        stateToId.put(state, id);

        // Analyze state properties
        FluidState fluid = state.getFluidState();
        boolean isFluid = !fluid.isEmpty();
        boolean isTranslucent = !state.canOcclude() || isFluid || state.is(Blocks.WATER) || state.is(Blocks.ICE);

        byte flag = 0;
        if (isFluid) {
            flag = 2;
        } else if (isTranslucent) {
            flag = 1;
        }
        renderTypeFlags[id] = flag;

        return id;
    }

    public BlockState getState(int id) {
        if (id < 0 || id >= MAX_MATERIALS) return Blocks.AIR.defaultBlockState();
        BlockState s = idToState[id];
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }

    public boolean isTranslucent(int id) {
        if (id <= 0 || id >= MAX_MATERIALS) return false;
        return renderTypeFlags[id] == 1 || renderTypeFlags[id] == 2;
    }

    public boolean isFluid(int id) {
        if (id <= 0 || id >= MAX_MATERIALS) return false;
        return renderTypeFlags[id] == 2;
    }

    public int size() {
        return nextId.get();
    }
}
