package me.ling.horizons2.engine.voxel;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Resolves authentic texture-averaged RGB colors and transparency values for Minecraft blocks.
 * Eliminates the reliance on coarse MapColor pigments.
 */
public class BlockColorResolver {
    private static final Map<Block, Integer> BLOCK_COLORS = new IdentityHashMap<>(256);

    static {
        // --- Natural Terrain ---
        register(Blocks.GRASS_BLOCK, 0x5C8E32);
        register(Blocks.DIRT, 0x866043);
        register(Blocks.COARSE_DIRT, 0x77553B);
        register(Blocks.ROOTED_DIRT, 0x90674C);
        register(Blocks.MUD, 0x3C393D);
        register(Blocks.CLAY, 0xA0A6B3);
        register(Blocks.GRAVEL, 0x837F7E);
        register(Blocks.PODZOL, 0x5B3F1E);
        register(Blocks.MYCELIUM, 0x6F6265);
        register(Blocks.FARMLAND, 0x603C20);
        register(Blocks.DIRT_PATH, 0x947841);

        // --- Stone & Deep Rocks ---
        register(Blocks.STONE, 0x797979);
        register(Blocks.COBBLESTONE, 0x808080);
        register(Blocks.MOSSY_COBBLESTONE, 0x6E7B5C);
        register(Blocks.SMOOTH_STONE, 0xA0A0A0);
        register(Blocks.STONE_BRICKS, 0x7A7A7A);
        register(Blocks.MOSSY_STONE_BRICKS, 0x697858);
        register(Blocks.CRACKED_STONE_BRICKS, 0x747474);
        register(Blocks.ANDESITE, 0x888889);
        register(Blocks.POLISHED_ANDESITE, 0x848485);
        register(Blocks.DIORITE, 0xBDBDBD);
        register(Blocks.POLISHED_DIORITE, 0xC4C4C5);
        register(Blocks.GRANITE, 0x9B6B59);
        register(Blocks.POLISHED_GRANITE, 0x9A6A58);
        register(Blocks.DEEPSLATE, 0x4C4C51);
        register(Blocks.COBBLED_DEEPSLATE, 0x4D4D50);
        register(Blocks.POLISHED_DEEPSLATE, 0x484849);
        register(Blocks.DEEPSLATE_BRICKS, 0x464647);
        register(Blocks.DEEPSLATE_TILES, 0x363638);
        register(Blocks.TUFF, 0x6C6D66);
        register(Blocks.BEDROCK, 0x333333);
        register(Blocks.OBSIDIAN, 0x14121E);
        register(Blocks.CRYING_OBSIDIAN, 0x22123A);

        // --- Sand, Sandstone & Deserts ---
        register(Blocks.SAND, 0xDBD3A0);
        register(Blocks.SANDSTONE, 0xD8CA9B);
        register(Blocks.SMOOTH_SANDSTONE, 0xDED2A7);
        register(Blocks.RED_SAND, 0xBD672F);
        register(Blocks.RED_SANDSTONE, 0xB66228);
        register(Blocks.SMOOTH_RED_SANDSTONE, 0xBA662B);

        // --- Badlands / Terracotta (Authentic Minecraft Strata Palette) ---
        register(Blocks.TERRACOTTA, 0x985E43);
        register(Blocks.WHITE_TERRACOTTA, 0xD1B1A1);
        register(Blocks.ORANGE_TERRACOTTA, 0xA15325);
        register(Blocks.MAGENTA_TERRACOTTA, 0x95576C);
        register(Blocks.LIGHT_BLUE_TERRACOTTA, 0x706C8A);
        register(Blocks.YELLOW_TERRACOTTA, 0xBA8523);
        register(Blocks.LIME_TERRACOTTA, 0x677535);
        register(Blocks.PINK_TERRACOTTA, 0xA04D4E);
        register(Blocks.GRAY_TERRACOTTA, 0x392A23);
        register(Blocks.LIGHT_GRAY_TERRACOTTA, 0x876B62);
        register(Blocks.CYAN_TERRACOTTA, 0x575B5B);
        register(Blocks.PURPLE_TERRACOTTA, 0x764556);
        register(Blocks.BLUE_TERRACOTTA, 0x4A3B5B);
        register(Blocks.BROWN_TERRACOTTA, 0x4D3323);
        register(Blocks.GREEN_TERRACOTTA, 0x4C532A);
        register(Blocks.RED_TERRACOTTA, 0x8E3C2E);
        register(Blocks.BLACK_TERRACOTTA, 0x251610);

        // --- Water & Ice ---
        register(Blocks.WATER, 0x28638A); // Realistic deep natural cyan-blue
        register(Blocks.ICE, 0x91B5E8);
        register(Blocks.PACKED_ICE, 0x8AB4E4);
        register(Blocks.BLUE_ICE, 0x74A7EE);
        register(Blocks.SNOW, 0xF9FEFE);
        register(Blocks.SNOW_BLOCK, 0xF9FEFE);

        // --- Wood & Planks ---
        register(Blocks.OAK_LOG, 0x675231);
        register(Blocks.OAK_PLANKS, 0xA2824E);
        register(Blocks.SPRUCE_LOG, 0x3B2611);
        register(Blocks.SPRUCE_PLANKS, 0x684E2F);
        register(Blocks.BIRCH_LOG, 0xDCD7CA);
        register(Blocks.BIRCH_PLANKS, 0xC4B07B);
        register(Blocks.JUNGLE_LOG, 0x554419);
        register(Blocks.JUNGLE_PLANKS, 0xA07351);
        register(Blocks.ACACIA_LOG, 0x68615A);
        register(Blocks.ACACIA_PLANKS, 0xA85A32);
        register(Blocks.DARK_OAK_LOG, 0x302211);
        register(Blocks.DARK_OAK_PLANKS, 0x422B15);
        register(Blocks.MANGROVE_LOG, 0x53291F);
        register(Blocks.MANGROVE_PLANKS, 0x753630);
        register(Blocks.CHERRY_LOG, 0x351F23);
        register(Blocks.CHERRY_PLANKS, 0xE0B2A8);
        register(Blocks.BAMBOO_BLOCK, 0x5F7A2F);
        register(Blocks.BAMBOO_PLANKS, 0xC09E38);

        // --- Foliage & Leaves ---
        register(Blocks.OAK_LEAVES, 0x487922);
        register(Blocks.SPRUCE_LEAVES, 0x3D5B3A);
        register(Blocks.BIRCH_LEAVES, 0x608534);
        register(Blocks.JUNGLE_LEAVES, 0x3D7A1D);
        register(Blocks.ACACIA_LEAVES, 0x4C6A1E);
        register(Blocks.DARK_OAK_LEAVES, 0x34541B);
        register(Blocks.MANGROVE_LEAVES, 0x516D25);
        register(Blocks.CHERRY_LEAVES, 0xEAA2B8);
        register(Blocks.AZALEA_LEAVES, 0x5C7A28);
        register(Blocks.FLOWERING_AZALEA_LEAVES, 0x6E773E);

        // --- Corals & Oceans ---
        register(Blocks.TUBE_CORAL_BLOCK, 0x3154C4);
        register(Blocks.BRAIN_CORAL_BLOCK, 0xC84D8E);
        register(Blocks.BUBBLE_CORAL_BLOCK, 0x9B26AC);
        register(Blocks.FIRE_CORAL_BLOCK, 0xA22329);
        register(Blocks.HORN_CORAL_BLOCK, 0xCBC034);
        register(Blocks.DEAD_TUBE_CORAL_BLOCK, 0x807775);
        register(Blocks.DEAD_BRAIN_CORAL_BLOCK, 0x807775);
        register(Blocks.DEAD_BUBBLE_CORAL_BLOCK, 0x807775);
        register(Blocks.DEAD_FIRE_CORAL_BLOCK, 0x807775);
        register(Blocks.DEAD_HORN_CORAL_BLOCK, 0x807775);
        register(Blocks.SEAGRASS, 0x356A1F);
        register(Blocks.SEA_PICKLE, 0x5A6428);
        register(Blocks.KELP, 0x486421);
        register(Blocks.KELP_PLANT, 0x486421);

        // --- Nether & End ---
        register(Blocks.NETHERRACK, 0x652828);
        register(Blocks.BASALT, 0x515156);
        register(Blocks.POLISHED_BASALT, 0x5B5B60);
        register(Blocks.BLACKSTONE, 0x2A242B);
        register(Blocks.SOUL_SAND, 0x514035);
        register(Blocks.SOUL_SOIL, 0x4B3A2F);
        register(Blocks.GLOWSTONE, 0xAB8653);
        register(Blocks.MAGMA_BLOCK, 0x8E3F1F);
        register(Blocks.LAVA, 0xD35809);
        register(Blocks.END_STONE, 0xDBDE9E);
        register(Blocks.END_STONE_BRICKS, 0xD6DA94);
        register(Blocks.PURPUR_BLOCK, 0xA97DA9);
        register(Blocks.PURPUR_PILLAR, 0xAB80AB);
    }

    private static void register(Block block, int rgb) {
        BLOCK_COLORS.put(block, rgb);
    }

    public static int resolveRgb(BlockState state) {
        if (state == null) return 0x888888;

        Block block = state.getBlock();
        Integer custom = BLOCK_COLORS.get(block);
        if (custom != null) {
            return custom;
        }

        // Check fluids
        FluidState fluid = state.getFluidState();
        if (fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)) {
            return 0x28638A;
        }
        if (fluid.is(Fluids.LAVA) || fluid.is(Fluids.FLOWING_LAVA)) {
            return 0xD35809;
        }

        // Fallback to MapColor if safe
        try {
            MapColor mc = state.getMapColor(null, null);
            if (mc != null && mc.col != 0) {
                return mc.col;
            }
        } catch (Exception ignored) {}

        return 0x7E7E7E;
    }

    public static float resolveAlpha(BlockState state, boolean isTranslucent, boolean isFluid) {
        if (state == null) return 1.0f;

        Block block = state.getBlock();
        if (block == Blocks.WATER || isFluid || state.getFluidState().is(Fluids.WATER)) {
            return 0.22f; // Clear translucent ocean water allowing visibility of the seabed
        }

        if (block == Blocks.GLASS || block == Blocks.GLASS_PANE || block.getDescriptionId().contains("glass")) {
            return 0.30f;
        }

        if (block == Blocks.ICE) {
            return 0.65f;
        }

        if (block == Blocks.SLIME_BLOCK) {
            return 0.70f;
        }

        return isTranslucent ? 0.65f : 1.0f;
    }
}
