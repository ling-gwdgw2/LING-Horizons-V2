package me.ling.horizons2.engine.storage;

public class StorageEngineFactory {
    public enum EngineType {
        ROCKSDB,
        SQLITE
    }

    public static ILodStorageEngine createEngine(EngineType type) {
        switch (type) {
            case ROCKSDB:
                return new RocksDbStorageEngine();
            case SQLITE:
            default:
                return new SQLiteStorageEngine();
        }
    }
}
