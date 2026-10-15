package com.deem.zkui.dao;

import com.googlecode.flyway.core.Flyway;
import java.io.InputStream;
import java.sql.*;
import java.util.zip.CRC32;

/** Select a fresh MySQL dialect without editing or repairing an applied V1. */
final class HistoryMigrations {
    private HistoryMigrations() { }

    static void configure(Flyway flyway, Connection connection) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        if (!"MySQL".equalsIgnoreCase(metadata.getDatabaseProductName())) return;
        if (metadata.getDatabaseMajorVersion() < 8 ||
                (metadata.getDatabaseMajorVersion() == 8 && metadata.getDatabaseMinorVersion() < 4))
            throw new IllegalStateException("MySQL 8.4 or newer is required");

        boolean versionTable = false;
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) if ("schema_version".equalsIgnoreCase(tables.getString("TABLE_NAME"))) versionTable = true;
        }
        if (!versionTable) {
            flyway.setLocations("db/mysql-migration");
            return; // Flyway still refuses unknown nonempty schemas; never auto-baseline.
        }
        int checksum;
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT checksum, script, success FROM schema_version WHERE version='1'")) {
            if (!rows.next()) throw new IllegalStateException("Missing historical V1 record");
            checksum = rows.getInt("checksum");
            if (rows.wasNull() || !"V1__Core.sql".equals(rows.getString("script")) || !rows.getBoolean("success") || rows.next())
                throw new IllegalStateException("Unrecognized historical V1 record");
        }
        if (checksum == checksum("/db/mysql-migration/V1__Core.sql")) {
            flyway.setLocations("db/mysql-migration");
        } else if (checksum != checksum("/db/migration/V1__Core.sql")) {
            throw new IllegalStateException("Unrecognized historical V1 checksum");
        }
        // An applied original V1 remains on its original location/checksum, with no repair.
    }

    private static int checksum(String resource) throws Exception {
        try (InputStream input = HistoryMigrations.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Migration resource unavailable");
            CRC32 checksum = new CRC32();
            checksum.update(input.readAllBytes()); // Original Flyway2 checksum algorithm.
            return (int) checksum.getValue();
        }
    }
}
