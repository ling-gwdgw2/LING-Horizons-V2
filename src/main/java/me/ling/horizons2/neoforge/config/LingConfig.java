package me.ling.horizons2.neoforge.config;

import me.ling.horizons2.engine.storage.StorageEngineFactory.EngineType;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class LingConfig {
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<Client, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = pair.getLeft();
        CLIENT_SPEC = pair.getRight();
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.IntValue renderDistance;
        public final ModConfigSpec.DoubleValue curvatureRadius;
        public final ModConfigSpec.EnumValue<EngineType> storageEngine;
        public final ModConfigSpec.BooleanValue enableHiZCulling;

        public Client(ModConfigSpec.Builder builder) {
            builder.comment("LING Horizons 2.0 Client Configuration").push("client");

            enabled = builder
                .comment("Enable GPU-Driven Voxel LOD Rendering")
                .define("enabled", true);

            renderDistance = builder
                .comment("LOD Render Distance in Chunks (16 - 256)")
                .defineInRange("renderDistance", 64, 16, 256);

            curvatureRadius = builder
                .comment("Planetary Spherical Curvature Radius in blocks (0.0 = Flat Horizon, 50000.0 = Realistic Earth Curve)")
                .defineInRange("curvatureRadius", 0.0, 0.0, 500000.0);

            storageEngine = builder
                .comment("Chunk LOD Persistence Backend (ROCKSDB or SQLITE)")
                .defineEnum("storageEngine", EngineType.SQLITE);

            enableHiZCulling = builder
                .comment("Enable GPU Hi-Z Occlusion Culling (discards occluded sections behind mountains/terrain)")
                .define("enableHiZCulling", true);

            builder.pop();
        }
    }
}
