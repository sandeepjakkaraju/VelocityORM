package com.velocityorm.core.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * @author sandeepkumarjakkaraju
 */
public class MigrationManager {
    private static final Logger log = LoggerFactory.getLogger(MigrationManager.class);
    private final DataSource dataSource;
    private final String location;

    public MigrationManager(DataSource dataSource) {
        this(dataSource, "db/migration");
    }

    public MigrationManager(DataSource dataSource, String location) {
        this.dataSource = dataSource;
        this.location = location;
    }

    public void migrate() throws Exception {
        log.info("Running database migrations from location: {}", location);
        
        try (Connection conn = dataSource.getConnection()) {
            ensureHistoryTableExists(conn);
            
            List<MigrationFile> files = scanMigrationFiles();
            log.info("Found {} migration files", files.size());
            
            List<MigrationRun> executed = loadExecutedMigrations(conn);
            Map<String, MigrationRun> executedMap = new HashMap<>();
            for (MigrationRun m : executed) {
                executedMap.put(m.getVersion(), m);
            }
            
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                int installedRank = executed.size() + 1;
                for (MigrationFile file : files) {
                    MigrationRun run = executedMap.get(file.version);
                    if (run == null) {
                        log.info("Executing migration: {} - {}", file.version, file.description);
                        long startTime = System.currentTimeMillis();
                        
                        executeSqlScript(stmt, file.content);
                        
                        long endTime = System.currentTimeMillis();
                        int executionTime = (int) (endTime - startTime);
                        
                        recordMigration(conn, installedRank++, file, executionTime, true);
                        log.info("Migration {} executed successfully in {} ms", file.version, executionTime);
                    } else {
                        if (run.getChecksum() != file.checksum) {
                            throw new IllegalStateException("Checksum mismatch for migration " + file.version + 
                                    ". Database: " + run.getChecksum() + ", Classpath: " + file.checksum);
                        }
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void ensureHistoryTableExists(Connection conn) throws SQLException {
        String dbName = conn.getMetaData().getDatabaseProductName().toLowerCase();
        String createTableSql;
        if (dbName.contains("postgresql") || dbName.contains("postgre")) {
            createTableSql = "CREATE TABLE IF NOT EXISTS velocity_schema_history (" +
                    "installed_rank INT PRIMARY KEY," +
                    "version VARCHAR(50) NOT NULL," +
                    "description VARCHAR(200)," +
                    "type VARCHAR(20) NOT NULL," +
                    "script VARCHAR(1000) NOT NULL," +
                    "checksum INT NOT NULL," +
                    "installed_by VARCHAR(100) NOT NULL," +
                    "installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "execution_time INT NOT NULL," +
                    "success BOOLEAN NOT NULL" +
                    ")";
        } else if (dbName.contains("mysql")) {
            createTableSql = "CREATE TABLE IF NOT EXISTS velocity_schema_history (" +
                    "installed_rank INT PRIMARY KEY," +
                    "version VARCHAR(50) NOT NULL," +
                    "description VARCHAR(200)," +
                    "type VARCHAR(20) NOT NULL," +
                    "script VARCHAR(1000) NOT NULL," +
                    "checksum INT NOT NULL," +
                    "installed_by VARCHAR(100) NOT NULL," +
                    "installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "execution_time INT NOT NULL," +
                    "success TINYINT(1) NOT NULL" +
                    ")";
        } else {
            createTableSql = "CREATE TABLE velocity_schema_history (" +
                    "installed_rank INT PRIMARY KEY," +
                    "version VARCHAR(50) NOT NULL," +
                    "description VARCHAR(200)," +
                    "type VARCHAR(20) NOT NULL," +
                    "script VARCHAR(1000) NOT NULL," +
                    "checksum INT NOT NULL," +
                    "installed_by VARCHAR(100) NOT NULL," +
                    "installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "execution_time INT NOT NULL," +
                    "success BOOLEAN NOT NULL" +
                    ")";
        }

        try (Statement stmt = conn.createStatement()) {
            if (dbName.contains("mysql") || dbName.contains("postgresql") || dbName.contains("postgre")) {
                stmt.execute(createTableSql);
            } else {
                try {
                    stmt.execute(createTableSql);
                } catch (SQLException e) {
                    // Check if table already exists, ignore
                }
            }
        }
    }

    private List<MigrationRun> loadExecutedMigrations(Connection conn) throws SQLException {
        List<MigrationRun> list = new ArrayList<>();
        String sql = "SELECT installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success " +
                "FROM velocity_schema_history ORDER BY installed_rank ASC";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new MigrationRun(
                        rs.getInt("installed_rank"),
                        rs.getString("version"),
                        rs.getString("description"),
                        rs.getString("type"),
                        rs.getString("script"),
                        rs.getInt("checksum"),
                        rs.getString("installed_by"),
                        rs.getTimestamp("installed_on"),
                        rs.getInt("execution_time"),
                        rs.getBoolean("success")
                ));
            }
        } catch (SQLException e) {
            // history table might not exist if create was skipped/failed
            return list;
        }
        return list;
    }

    private void recordMigration(Connection conn, int rank, MigrationFile file, int executionTime, boolean success) throws SQLException {
        String sql = "INSERT INTO velocity_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rank);
            ps.setString(2, file.version);
            ps.setString(3, file.description);
            ps.setString(4, "SQL");
            ps.setString(5, file.filename);
            ps.setInt(6, file.checksum);
            ps.setString(7, "VelocityORM");
            ps.setInt(8, executionTime);
            ps.setBoolean(9, success);
            ps.executeUpdate();
        }
    }

    private void executeSqlScript(Statement stmt, String sqlContent) throws SQLException {
        String[] statements = sqlContent.split(";");
        for (String sql : statements) {
            String trimmed = sql.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                stmt.execute(trimmed);
            }
        }
    }

    private List<MigrationFile> scanMigrationFiles() throws Exception {
        List<MigrationFile> files = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = cl.getResources(location);
        
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            URI uri = url.toURI();
            
            Path path;
            FileSystem fs = null;
            if (uri.getScheme().equals("jar")) {
                try {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                } catch (FileSystemAlreadyExistsException e) {
                    fs = FileSystems.getFileSystem(uri);
                }
                path = fs.getPath("/" + location);
            } else {
                path = Paths.get(uri);
            }
            
            try (var walk = Files.walk(path, 1)) {
                List<Path> filePaths = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".sql"))
                        .collect(Collectors.toList());
                        
                for (Path p : filePaths) {
                    String filename = p.getFileName().toString();
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    files.add(parseMigrationFile(filename, content));
                }
            } finally {
                if (fs != null && uri.getScheme().equals("jar")) {
                    fs.close();
                }
            }
        }
        
        files.sort(Comparator.comparing(f -> f.version));
        return files;
    }

    private MigrationFile parseMigrationFile(String filename, String content) {
        if (!filename.startsWith("V") || !filename.contains("__")) {
            throw new IllegalArgumentException("Invalid migration filename format: " + filename + ". Must be V<Version>__<Description>.sql");
        }
        
        String[] parts = filename.split("__", 2);
        String version = parts[0].substring(1);
        String descWithExtension = parts[1];
        String description = descWithExtension.substring(0, descWithExtension.lastIndexOf('.')).replace("_", " ");
        
        CRC32 crc = new CRC32();
        crc.update(content.getBytes(StandardCharsets.UTF_8));
        int checksum = (int) crc.getValue();
        
        return new MigrationFile(filename, version, description, content, checksum);
    }

    private static class MigrationFile {
        final String filename;
        final String version;
        final String description;
        final String content;
        final int checksum;

        MigrationFile(String filename, String version, String description, String content, int checksum) {
            this.filename = filename;
            this.version = version;
            this.description = description;
            this.content = content;
            this.checksum = checksum;
        }
    }

    private static class MigrationRun {
        private final int installedRank;
        private final String version;
        private final String description;
        private final String type;
        private final String script;
        private final int checksum;
        private final String installedBy;
        private final Timestamp installedOn;
        private final int executionTime;
        private final boolean success;

        MigrationRun(int installedRank, String version, String description, String type, String script, int checksum, String installedBy, Timestamp installedOn, int executionTime, boolean success) {
            this.installedRank = installedRank;
            this.version = version;
            this.description = description;
            this.type = type;
            this.script = script;
            this.checksum = checksum;
            this.installedBy = installedBy;
            this.installedOn = installedOn;
            this.executionTime = executionTime;
            this.success = success;
        }

        public String getVersion() { return version; }
        public int getChecksum() { return checksum; }
    }
}
