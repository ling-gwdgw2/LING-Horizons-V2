package me.ling.horizons2.engine.storage;

import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteOptions;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Embedded RocksDB Storage Engine for LING Horizons 2.0.
 * High write-throughput LSM-tree engine optimized for world pregeneration.
 */
public class RocksDbStorageEngine implements ILodStorageEngine {
    static {
        RocksDB.loadLibrary();
    }

    private RocksDB db;
    private Options options;
    private WriteOptions writeOptions;

    @Override
    public void initialize(Path storageDirectory) throws Exception {
        Files.createDirectories(storageDirectory);
        Path dbPath = storageDirectory.resolve("lod_rocksdb");

        options = new Options();
        options.setCreateIfMissing(true);
        options.setCompressionType(CompressionType.LZ4_COMPRESSION);
        options.setWriteBufferSize(32 * 1024 * 1024); // 32 MB write buffer
        options.setMaxWriteBufferNumber(3);

        writeOptions = new WriteOptions();
        writeOptions.setSync(false);

        db = RocksDB.open(options, dbPath.toAbsolutePath().toString());
    }

    private byte[] makeKey(int x, int y, int z) {
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.putInt(x);
        buf.putInt(y);
        buf.putInt(z);
        return buf.array();
    }

    @Override
    public byte[] loadSection(int sectionX, int sectionY, int sectionZ) throws Exception {
        if (db == null) return null;
        return db.get(makeKey(sectionX, sectionY, sectionZ));
    }

    @Override
    public void saveSection(int sectionX, int sectionY, int sectionZ, byte[] data) throws Exception {
        if (db == null) return;
        db.put(writeOptions, makeKey(sectionX, sectionY, sectionZ), data);
    }

    @Override
    public void deleteSection(int sectionX, int sectionY, int sectionZ) throws Exception {
        if (db == null) return;
        db.delete(writeOptions, makeKey(sectionX, sectionY, sectionZ));
    }

    @Override
    public void flush() throws Exception {
        // RocksDB handles background flushing
    }

    @Override
    public String getEngineName() {
        return "RocksDB 10.2";
    }

    @Override
    public void close() throws Exception {
        if (writeOptions != null) writeOptions.close();
        if (db != null) {
            db.close();
            db = null;
        }
        if (options != null) options.close();
    }
}
