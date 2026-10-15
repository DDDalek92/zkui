package com.deem.zkui.dao;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Properties;
import java.util.zip.CRC32;
import org.flywaydb.core.Flyway;

/** Fresh or explicitly offline-bridged H2 only; no automatic old-file/metadata conversion. */
final class ModernH2History {
    private ModernH2History() { }

    static String url(String configured) throws SQLException {
        if (configured == null || !configured.startsWith("jdbc:h2:")) throw new SQLException("H2 URL is required");
        int separator = configured.indexOf(';');
        String base = configured.substring(8, separator < 0 ? configured.length() : separator);
        String filePrefix = base.startsWith("file:") ? "file:" : "";
        String path = base.substring(filePrefix.length());
        // H2 2 rejects the old sample's implicit relative jdbc:h2:zkui form.
        if (!path.isEmpty() && path.indexOf(':') < 0 && !path.startsWith("/") && !path.startsWith("\\")
                && !path.startsWith("./") && !path.startsWith("../") && !path.startsWith("~/")
                && !path.startsWith(".\\") && !path.startsWith("..\\") && !path.startsWith("~\\")) {
            configured = "jdbc:h2:" + filePrefix + "./" + path + (separator < 0 ? "" : configured.substring(separator));
        }
        // H2's mode is not persisted across closing the database. Apply it on every connection.
        boolean hasMode = false;
        for (String setting : configured.substring(configured.indexOf(';') + 1).split(";")) {
            String[] pair = setting.split("=", 2);
            String key = pair[0].trim().toUpperCase(java.util.Locale.ROOT);
            if ("MODE".equals(key)) {
                if (hasMode || pair.length != 2 || !"LEGACY".equalsIgnoreCase(pair[1].trim()))
                    throw new SQLException("History H2 requires LEGACY compatibility mode");
                hasMode = true;
            } else if ("INIT".equals(key) || "SCHEMA_SEARCH_PATH".equals(key)
                    || ("SCHEMA".equals(key) && (pair.length != 2 || !"PUBLIC".equals(pair[1].trim())))
                    || (("DATABASE_TO_LOWER".equals(key) || "CASE_INSENSITIVE_IDENTIFIERS".equals(key))
                        && (pair.length != 2 || !"FALSE".equalsIgnoreCase(pair[1].trim())))
                    || ("DATABASE_TO_UPPER".equals(key) && (pair.length != 2 || !"TRUE".equalsIgnoreCase(pair[1].trim())))) {
                throw new SQLException("Unsupported history H2 initialization or identifier settings");
            }
        }
        return hasMode ? configured : configured + ";MODE=LEGACY";
    }

    static void migrate(Properties properties) throws Exception {
        H2FileFormatGuard.check("org.h2.Driver", properties.getProperty("jdbcUrl"));
        Class.forName("org.h2.Driver");
        String jdbc = url(properties.getProperty("jdbcUrl"));
        String user = properties.getProperty("jdbcUser");
        String password = properties.getProperty("jdbcPwd");
        String location;
        try (Connection connection = DriverManager.getConnection(jdbc, user, password)) {
            if (!connection.getMetaData().getDatabaseProductVersion().startsWith("2.5.252 "))
                throw new SQLException("Unvalidated H2 runtime version");
            if (!"PUBLIC".equals(connection.getSchema())) throw new SQLException("Unsupported history H2 default schema");
            location = location(connection);
        }
        // The modern H2 path never treats env=dev as permission to erase applied history.
        Flyway flyway = configured(jdbc, user, password, location);
        flyway.migrate();
        flyway.validate();
    }

    private static Flyway configured(String url, String user, String password, String location) {
        return Flyway.configure().dataSource(url, user, password).table("schema_version")
                .schemas("PUBLIC").defaultSchema("PUBLIC")
                .locations(location).baselineOnMigrate(false).validateOnMigrate(true)
                .cleanDisabled(true).load();
    }

    private static String location(Connection connection) throws Exception {
        boolean versionTable = false;
        boolean otherTable = false;
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String schema = tables.getString("TABLE_SCHEM");
                if ("INFORMATION_SCHEMA".equals(schema)) continue;
                if ("PUBLIC".equals(schema) && "schema_version".equals(tables.getString("TABLE_NAME"))) versionTable = true;
                else otherTable = true;
            }
        }
        if (!versionTable) {
            if (otherTable) throw new SQLException("Unrecognized nonempty history schema");
            return "db/h2-migration";
        }
        try (ResultSet columns = connection.getMetaData().getColumns(null, "PUBLIC", "schema_version", "%")) {
            while (columns.next()) if ("version_rank".equalsIgnoreCase(columns.getString("COLUMN_NAME")))
                throw new SQLException("Offline historical metadata bridge is required");
        }
        int checksum;
        int versions = 0, markers = 0;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT \"installed_rank\", \"version\", \"type\", \"description\", \"script\", \"checksum\", \"success\", \"execution_time\" FROM \"schema_version\"")) {
            while (rows.next()) {
                if ("1".equals(rows.getString(2)) && rows.getInt(1) > 0) versions++;
                else if (rows.getInt(1) == -1 && rows.getObject(2) == null && "TABLE".equals(rows.getString(3))
                        && "<< Flyway Schema History table created >>".equals(rows.getString(4))
                        && "".equals(rows.getString(5)) && rows.getObject(6) == null
                        && rows.getBoolean(7) && rows.getInt(8) == 0) markers++;
                else throw new SQLException("Unrecognized migration history");
            }
        }
        if (versions != 1 || markers > 1) throw new SQLException("Unrecognized migration history");
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT \"checksum\", \"script\", \"success\", \"type\" FROM \"schema_version\" WHERE \"version\"='1'")) {
            if (!rows.next()) throw new SQLException("Missing historical V1");
            checksum = rows.getInt(1);
            if (rows.wasNull() || !"V1__Core.sql".equals(rows.getString(2)) || !rows.getBoolean(3)
                    || !"SQL".equals(rows.getString(4)) || rows.next()) throw new SQLException("Unrecognized historical V1");
        }
        validateSchema(connection);
        if (checksum == checksum("/db/h2-migration/V1__Core.sql")) return "db/h2-migration";
        if (checksum == checksum("/db/migration/V1__Core.sql")) return "db/migration";
        throw new SQLException("Unrecognized historical V1 checksum");
    }

    private static void validateSchema(Connection connection) throws SQLException {
        String[] names = {"ID", "CHANGE_USER", "CHANGE_DATE", "CHANGE_SUMMARY", "CHANGE_IP"};
        int[] types = {Types.BIGINT, Types.VARCHAR, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR};
        int[] widths = {0, 100, 0, 500, 500};
        int count = 0;
        try (ResultSet columns = connection.getMetaData().getColumns(null, "PUBLIC", "HISTORIES", "%")) {
            while (columns.next()) {
                int n = count++;
                if (n >= names.length || !names[n].equals(columns.getString("COLUMN_NAME"))
                        || types[n] != columns.getInt("DATA_TYPE")
                        || (widths[n] != 0 && widths[n] != columns.getInt("COLUMN_SIZE"))
                        || columns.getInt("NULLABLE") != (n == 0 ? DatabaseMetaData.columnNoNulls : DatabaseMetaData.columnNullable)
                        || (n == 0 && !"YES".equals(columns.getString("IS_AUTOINCREMENT"))))
                    throw new SQLException("Historical schema does not match the validated contract");
            }
        }
        if (count != names.length) throw new SQLException("Missing historical columns");
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", "HISTORIES")) {
            if (!keys.next() || !"ID".equals(keys.getString("COLUMN_NAME")) || keys.getInt("KEY_SEQ") != 1 || keys.next())
                throw new SQLException("Unrecognized historical primary key");
        }
    }

    private static int checksum(String resource) throws Exception {
        try (InputStream input = ModernH2History.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing migration resource");
            CRC32 crc = new CRC32();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return (int) crc.getValue();
        }
    }
}
