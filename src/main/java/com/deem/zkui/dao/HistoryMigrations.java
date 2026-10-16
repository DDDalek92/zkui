package com.deem.zkui.dao;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Properties;
import java.util.zip.CRC32;
import org.flywaydb.core.Flyway;

/** Fresh or explicitly offline-bridged MySQL only. No automatic metadata conversion. */
final class HistoryMigrations {
    private HistoryMigrations() { }

    static void migrate(Properties properties) throws Exception {
        Class.forName(properties.getProperty("jdbcClass"));
        String url = properties.getProperty("jdbcUrl");
        String user = properties.getProperty("jdbcUser"), password = properties.getProperty("jdbcPwd");
        String catalog, location;
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!"MySQL".equalsIgnoreCase(metadata.getDatabaseProductName())
                    || metadata.getDatabaseMajorVersion() < 8
                    || (metadata.getDatabaseMajorVersion() == 8 && metadata.getDatabaseMinorVersion() < 4))
                throw new SQLException("MySQL 8.4 or newer is required");
            catalog = connection.getCatalog();
            if (catalog == null || catalog.isEmpty()) throw new SQLException("A history database must be selected");
            location = location(connection, catalog);
        }
        Flyway flyway = Flyway.configure().dataSource(url, user, password).table("schema_version")
                .schemas(catalog).defaultSchema(catalog).locations(location)
                .cleanDisabled(true).baselineOnMigrate(false).validateOnMigrate(true).load();
        flyway.migrate(); flyway.validate();
    }

    private static String location(Connection connection, String catalog) throws Exception {
        boolean versionTable = false, other = false;
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (tables.next()) {
                if ("schema_version".equals(tables.getString("TABLE_NAME"))
                        && ("TABLE".equals(tables.getString("TABLE_TYPE")) || "BASE TABLE".equals(tables.getString("TABLE_TYPE")))) versionTable = true;
                else other = true;
            }
        }
        if (!versionTable) {
            if (other) throw new SQLException("Unrecognized nonempty history database");
            return "db/mysql-migration";
        }
        try (ResultSet columns = metadata.getColumns(catalog, null, "schema\\_version", "%")) {
            while (columns.next()) if ("version_rank".equalsIgnoreCase(columns.getString("COLUMN_NAME")))
                throw new SQLException("Offline historical metadata bridge is required");
        }
        int checksum;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT installed_rank, version, type, script, checksum, success FROM schema_version")) {
            if (!rows.next() || rows.getInt(1) <= 0 || !"1".equals(rows.getString(2))
                    || !"SQL".equals(rows.getString(3)) || !"V1__Core.sql".equals(rows.getString(4)))
                throw new SQLException("Unrecognized migration history");
            checksum = rows.getInt(5);
            if (rows.wasNull() || !rows.getBoolean(6) || rows.next()) throw new SQLException("Unrecognized migration history");
        }
        validateSchema(connection, catalog);
        if (checksum == checksum("/db/mysql-migration/V1__Core.sql")) return "db/mysql-migration";
        if (checksum == checksum("/db/migration/V1__Core.sql")) return "db/migration";
        throw new SQLException("Unrecognized historical V1 checksum");
    }

    private static void validateSchema(Connection connection, String catalog) throws SQLException {
        String[] names = {"id", "change_user", "change_date", "change_summary", "change_ip"};
        int[] types = {Types.BIGINT, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR};
        int[] widths = {0, 100, 0, 500, 500};
        int count = 0;
        try (ResultSet columns = connection.getMetaData().getColumns(catalog, null, "histories", "%")) {
            while (columns.next()) {
                int n = count++;
                if (n >= names.length || !names[n].equalsIgnoreCase(columns.getString("COLUMN_NAME"))
                        || types[n] != columns.getInt("DATA_TYPE")
                        || (widths[n] != 0 && widths[n] != columns.getInt("COLUMN_SIZE"))
                        || columns.getInt("NULLABLE") != (n == 0 ? DatabaseMetaData.columnNoNulls : DatabaseMetaData.columnNullable)
                        || (n == 0 && !"YES".equals(columns.getString("IS_AUTOINCREMENT"))))
                    throw new SQLException("Historical schema does not match the validated contract");
            }
        }
        if (count != names.length) throw new SQLException("Missing historical columns");
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(catalog, null, "histories")) {
            if (!keys.next() || !"id".equalsIgnoreCase(keys.getString("COLUMN_NAME")) || keys.getInt("KEY_SEQ") != 1 || keys.next())
                throw new SQLException("Unrecognized historical primary key");
        }
    }

    private static int checksum(String resource) throws Exception {
        try (InputStream input = HistoryMigrations.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Migration resource unavailable");
            CRC32 crc = new CRC32();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return (int) crc.getValue();
        }
    }
}
