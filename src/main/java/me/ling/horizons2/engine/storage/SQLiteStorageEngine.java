package me.ling.horizons2.engine.storage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Embedded SQLite Storage Engine for LING Horizons 2.0.
 * Standardized relational storage with zero external service dependencies.
 */
public class SQLiteStorageEngine implements ILodStorageEngine {
    private Connection connection;
    private PreparedStatement selectStmt;
    private PreparedStatement insertStmt;
    private PreparedStatement deleteStmt;

    @Override
    public void initialize(Path storageDirectory) throws Exception {
        Files.createDirectories(storageDirectory);
        Path dbPath = storageDirectory.resolve("lod_storage.sqlite");

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        connection = DriverManager.getConnection(url);

        // Performance tuning for fast writes
        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA temp_store = MEMORY;");
            stmt.execute("CREATE TABLE IF NOT EXISTS sections (x INT, y INT, z INT, data BLOB, PRIMARY KEY(x, y, z));");
        }

        selectStmt = connection.prepareStatement("SELECT data FROM sections WHERE x = ? AND y = ? AND z = ?;");
        insertStmt = connection.prepareStatement("INSERT OR REPLACE INTO sections (x, y, z, data) VALUES (?, ?, ?, ?);");
        deleteStmt = connection.prepareStatement("DELETE FROM sections WHERE x = ? AND y = ? AND z = ?;");
    }

    @Override
    public synchronized byte[] loadSection(int sectionX, int sectionY, int sectionZ) throws Exception {
        if (connection == null) return null;
        selectStmt.setInt(1, sectionX);
        selectStmt.setInt(2, sectionY);
        selectStmt.setInt(3, sectionZ);
        try (ResultSet rs = selectStmt.executeQuery()) {
            if (rs.next()) {
                return rs.getBytes("data");
            }
        }
        return null;
    }

    @Override
    public synchronized void saveSection(int sectionX, int sectionY, int sectionZ, byte[] data) throws Exception {
        if (connection == null) return;
        insertStmt.setInt(1, sectionX);
        insertStmt.setInt(2, sectionY);
        insertStmt.setInt(3, sectionZ);
        insertStmt.setBytes(4, data);
        insertStmt.executeUpdate();
    }

    @Override
    public synchronized void deleteSection(int sectionX, int sectionY, int sectionZ) throws Exception {
        if (connection == null) return;
        deleteStmt.setInt(1, sectionX);
        deleteStmt.setInt(2, sectionY);
        deleteStmt.setInt(3, sectionZ);
        deleteStmt.executeUpdate();
    }

    @Override
    public synchronized void flush() throws Exception {
        // WAL auto-checkpoints under normal operation
    }

    @Override
    public String getEngineName() {
        return "SQLite";
    }

    @Override
    public synchronized void close() throws Exception {
        if (selectStmt != null) selectStmt.close();
        if (insertStmt != null) insertStmt.close();
        if (deleteStmt != null) deleteStmt.close();
        if (connection != null && !connection.isClosed()) connection.close();
    }
}
