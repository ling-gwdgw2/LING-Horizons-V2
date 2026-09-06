package me.ling.horizons2.engine.storage;

import me.ling.horizons2.engine.voxel.VoxelData;
import me.ling.horizons2.engine.voxel.VoxelGrid32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class StorageEngineTest {

    @Test
    public void testSerializationRoundTrip() throws Exception {
        VoxelGrid32 grid = new VoxelGrid32();
        grid.set(5, 5, 5, VoxelData.pack(10, 15, 15, VoxelData.ALL_FACES, false, false, 2));
        grid.set(12, 18, 24, VoxelData.pack(25, 0, 8, VoxelData.FACE_UP, true, true, 0));

        byte[] bytes = SectionSerializer.serialize(grid);
        assertNotNull(bytes);
        assertTrue(bytes.length > 2);

        VoxelGrid32 restored = SectionSerializer.deserialize(bytes);
        assertEquals(grid.get(5, 5, 5), restored.get(5, 5, 5));
        assertEquals(grid.get(12, 18, 24), restored.get(12, 18, 24));
        assertEquals(VoxelData.AIR, restored.get(0, 0, 0));
    }

    @Test
    public void testSQLiteEngine(@TempDir Path tempDir) throws Exception {
        try (ILodStorageEngine engine = new SQLiteStorageEngine()) {
            engine.initialize(tempDir);

            byte[] sampleData = new byte[] { 1, 2, 3, 4, 5 };
            engine.saveSection(10, 2, -15, sampleData);

            byte[] loaded = engine.loadSection(10, 2, -15);
            assertNotNull(loaded);
            assertArrayEquals(sampleData, loaded);

            engine.deleteSection(10, 2, -15);
            assertNull(engine.loadSection(10, 2, -15));
        }
    }
}
