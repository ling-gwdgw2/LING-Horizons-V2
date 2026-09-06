package me.ling.horizons2.engine.storage;

import java.nio.file.Path;

/**
 * Storage Engine Contract for LING Horizons 2.0.
 * Decouples chunk persistence from database implementation details.
 */
public interface ILodStorageEngine extends AutoCloseable {

    /**
     * Initializes the database backend in the specified world storage directory.
     */
    void initialize(Path storageDirectory) throws Exception;

    /**
     * Loads raw compressed voxel data for the specified 32x32x32 section coordinates.
     * Returns null if the section does not exist in persistence.
     */
    byte[] loadSection(int sectionX, int sectionY, int sectionZ) throws Exception;

    /**
     * Persists compressed voxel data for the specified section.
     */
    void saveSection(int sectionX, int sectionY, int sectionZ, byte[] data) throws Exception;

    /**
     * Removes section from persistent storage.
     */
    void deleteSection(int sectionX, int sectionY, int sectionZ) throws Exception;

    /**
     * Flushes any in-memory write buffers or WAL logs to disk.
     */
    void flush() throws Exception;

    /**
     * Returns the human-readable identifier of the database engine (e.g. "RocksDB", "LMDB", "SQLite").
     */
    String getEngineName();
}
