package me.ling.horizons2.engine.storage;

import me.ling.horizons2.engine.voxel.VoxelData;
import me.ling.horizons2.engine.voxel.VoxelGrid32;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * High-performance binary serializer and compressor for VoxelGrid32.
 */
public class SectionSerializer {
    public static final byte FORMAT_VERSION = 1;

    public static byte[] serialize(VoxelGrid32 grid) {
        if (grid.isEmpty()) {
            return new byte[] { FORMAT_VERSION, 0 }; // Empty marker
        }

        if (grid.isUniform()) {
            ByteBuffer buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
            buf.put(FORMAT_VERSION);
            buf.put((byte) 1); // isUniform = true
            buf.putInt(grid.getUniformVoxel());
            return buf.array();
        }

        // Heterogeneous section: compress 32K integers (128 KB)
        int[] raw = grid.getRawData();
        ByteBuffer rawBuf = ByteBuffer.allocate(raw.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : raw) {
            rawBuf.putInt(v);
        }
        byte[] uncompressed = rawBuf.array();

        // Compress using fast Deflater
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(uncompressed);
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        baos.write(FORMAT_VERSION);
        baos.write(2); // isHeterogeneous = 2

        byte[] chunk = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(chunk);
            baos.write(chunk, 0, count);
        }
        deflater.end();

        return baos.toByteArray();
    }

    public static VoxelGrid32 deserialize(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length < 2) {
            return new VoxelGrid32();
        }

        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte version = header.get();
        byte type = header.get();

        if (type == 0) {
            // Empty
            return new VoxelGrid32();
        } else if (type == 1) {
            // Uniform
            int uniformVoxel = header.getInt();
            return new VoxelGrid32(uniformVoxel);
        } else if (type == 2) {
            // Compressed heterogeneous
            Inflater inflater = new Inflater();
            inflater.setInput(bytes, 2, bytes.length - 2);

            byte[] uncompressed = new byte[VoxelGrid32.VOLUME * 4];
            int offset = 0;
            while (!inflater.finished() && offset < uncompressed.length) {
                int count = inflater.inflate(uncompressed, offset, uncompressed.length - offset);
                offset += count;
            }
            inflater.end();

            ByteBuffer dataBuf = ByteBuffer.wrap(uncompressed).order(ByteOrder.LITTLE_ENDIAN);
            VoxelGrid32 grid = new VoxelGrid32();
            int[] raw = grid.getRawData();
            for (int i = 0; i < raw.length; i++) {
                raw[i] = dataBuf.getInt();
            }
            return grid;
        }

        return new VoxelGrid32();
    }
}
