package com.deem.zkui.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Child-process helper for synthetic fixtures only, deliberately independent of JUnit and the application. */
public final class H2MigrationProbe {
    private static final long HIGH_ID = 9007199254740993L;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Path root = Path.of(args[1]).toRealPath();
        if (!Files.readString(root.resolve("owned-fixture")).equals(args[2])) throw new AssertionError("Not test-owned");
        if (Collections.list(H2MigrationProbe.class.getClassLoader().getResources("org/h2/Driver.class")).size() != 1)
            throw new AssertionError("Exactly one H2 driver must be visible");
        Class.forName("org.h2.Driver");
        switch (mode) {
            case "seed": seed(root); break;
            case "regular": importRegular(root); break;
            case "legacy": importLegacy(root); break;
            case "truncated": importTruncated(root); break;
            case "rollback": rollback(root); break;
            case "bridge": bridge(root); break;
            case "modern": modern(root); break;
            default: throw new AssertionError("Unknown test mode");
        }
    }

    private static String url(Path root, String directory) {
        return "jdbc:h2:" + root.resolve(directory).resolve("history").toAbsolutePath();
    }
    private static String literal(Path path) { return "'" + path.toString().replace('\\', '/').replace("'", "''") + "'"; }
    private static Connection connect(String url, String version) throws Exception {
        Connection connection = DriverManager.getConnection(url, "sa", "");
        if (!connection.getMetaData().getDatabaseProductVersion().startsWith(version + " ")) {
            connection.close();
            throw new AssertionError("Unexpected H2 driver version");
        }
        return connection;
    }

    private static void seed(Path root) throws Exception {
        Files.createDirectory(root.resolve("old-work"));
        String url = url(root, "old-work");
        Class<?> type = Class.forName("com.googlecode.flyway.core.Flyway");
        Object flyway = type.getConstructor().newInstance();
        type.getMethod("setDataSource", String.class, String.class, String.class).invoke(flyway, url, "sa", "");
        type.getMethod("migrate").invoke(flyway);
        try (Connection connection = connect(url, "1.3.174"); PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO histories (ID, CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP) VALUES (?, ?, ?, ?, ?)")) {
            insert.setLong(1, 41); insert.setString(2, null); insert.setTimestamp(3, null);
            insert.setString(4, ""); insert.setString(5, ""); insert.executeUpdate();
            insert.setLong(1, HIGH_ID); insert.setString(2, "旧用户");
            insert.setTimestamp(3, new Timestamp(1700000000123L));
            insert.setString(4, "  中文😀\nquote' slash\\  "); insert.setString(5, "127.0.0.1"); insert.executeUpdate();
            insert.setLong(1, HIGH_ID + 10); insert.setString(4, "x".repeat(500)); insert.executeUpdate();
            Files.writeString(root.resolve("expected-rows"), snapshot(connection));
            Files.writeString(root.resolve("expected-stable"), stableSnapshot(connection));
        }
        copyClosedDatabase(root.resolve("old-work"), root.resolve("sealed-source"));
        try (Connection connection = connect(url + ";IFEXISTS=TRUE", "1.3.174"); Statement statement = connection.createStatement()) {
            statement.execute("SCRIPT TO " + literal(root.resolve("export.sql")) + " CHARSET 'UTF-8'");
        }
        System.out.println("OLD_FIXTURE_SEALED_AND_EXPORTED");
    }

    private static void copyClosedDatabase(Path from, Path to) throws Exception {
        Files.createDirectory(to); // Existing targets are rejected, never overwritten.
        try (var files = Files.list(from)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file) || Files.isSymbolicLink(file) || file.toString().endsWith(".lock.db"))
                    throw new AssertionError("Database must be closed regular files");
                Files.copy(file, to.resolve(file.getFileName()));
            }
        }
    }

    private static void importRegular(Path root) throws Exception {
        Files.createDirectory(root.resolve("new-regular"));
        try (Connection connection = connect(url(root, "new-regular"), "2.5.252"); Statement statement = connection.createStatement()) {
            try {
                statement.execute("RUNSCRIPT FROM " + literal(root.resolve("export.sql")) + " CHARSET 'UTF-8' FROM_1X");
                throw new AssertionError("Expected the known DATETIME compatibility blocker");
            } catch (SQLException ex) {
                if (ex.getErrorCode() != 50004 || !ex.getMessage().contains("DATETIME")) throw ex;
                System.out.println("EXPECTED_REGULAR_MODE_BLOCKER_DATETIME_50004");
            }
        }
        // A failed import is intentionally not considered a usable or switched database.
    }

    private static void importLegacy(Path root) throws Exception {
        Files.createDirectory(root.resolve("new-legacy"));
        String url = url(root, "new-legacy") + ";MODE=LEGACY";
        try (Connection connection = connect(url, "2.5.252"); Statement statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM " + literal(root.resolve("export.sql")) + " CHARSET 'UTF-8' FROM_1X");
            assertSnapshot(root, connection);
        }
        // Reopen a separate connection, check imported rows/metadata, then exercise the identity sequence.
        try (Connection connection = connect(url + ";IFEXISTS=TRUE", "2.5.252")) {
            assertSnapshot(root, connection);
            appendAndCheck(connection, "synthetic-candidate-row");
        }
        try (Connection connection = connect(url + ";IFEXISTS=TRUE", "2.5.252"); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM histories")) {
            rows.next(); if (rows.getInt(1) != 4) throw new AssertionError("Restart lost rows");
        }
        System.out.println("LEGACY_ROWS_METADATA_ID_AND_REOPEN_OK");
    }

    private static void importTruncated(Path root) throws Exception {
        String original = Files.readString(root.resolve("export.sql"));
        int last = original.lastIndexOf(';');
        if (last < 1 || original.charAt(last - 1) != ')') throw new AssertionError("Unexpected synthetic export shape");
        Path truncated = root.resolve("export-truncated.sql");
        Files.writeString(truncated, original.substring(0, last - 1)); // Remove the final closing parenthesis/terminator.
        Files.createDirectory(root.resolve("new-truncated"));
        try (Connection connection = connect(url(root, "new-truncated") + ";MODE=LEGACY", "2.5.252");
                Statement statement = connection.createStatement()) {
            try {
                statement.execute("RUNSCRIPT FROM " + literal(truncated) + " CHARSET 'UTF-8' FROM_1X");
                throw new AssertionError("Truncated import must not be accepted");
            } catch (SQLSyntaxErrorException expected) {
                System.out.println("EXPECTED_TRUNCATED_SQL_SYNTAX_FAILURE");
            }
        }
    }

    private static void rollback(Path root) throws Exception {
        copyClosedDatabase(root.resolve("sealed-source"), root.resolve("rollback-work"));
        try (Connection connection = connect(url(root, "rollback-work") + ";IFEXISTS=TRUE", "1.3.174")) {
            assertSnapshot(root, connection);
            appendAndCheck(connection, "synthetic-rollback-row");
        }
        System.out.println("OLD_DRIVER_ROLLBACK_COPY_OK");
    }


    // Flyway 4.2 contains its own version_rank -> installed_rank metadata upgrade.
    // It runs only on old-work; the original sealed database and export remain untouched.
    private static void bridge(Path root) throws Exception {
        String jdbc = url(root, "old-work") + ";IFEXISTS=TRUE";
        Class<?> type = Class.forName("org.flywaydb.core.Flyway");
        Object flyway = type.getConstructor().newInstance();
        type.getMethod("setDataSource", String.class, String.class, String.class).invoke(flyway, jdbc, "sa", "");
        type.getMethod("setValidateOnMigrate", boolean.class).invoke(flyway, true);
        type.getMethod("migrate").invoke(flyway);
        try (Connection connection = connect(jdbc, "1.3.174"); Statement statement = connection.createStatement()) {
            assertStable(root, connection);
            try (ResultSet columns = connection.getMetaData().getColumns(null, "PUBLIC", "schema_version", "version_rank")) {
                if (columns.next()) throw new AssertionError("Bridge did not upgrade metadata");
            }
            statement.execute("SCRIPT TO " + literal(root.resolve("export-bridged.sql")) + " CHARSET 'UTF-8'");
        }
        System.out.println("FLYWAY4_SUPPORTED_METADATA_UPGRADE_OK");
    }

    private static void modern(Path root) throws Exception {
        Files.createDirectory(root.resolve("new-modern"));
        String jdbc = url(root, "new-modern") + ";MODE=LEGACY";
        try (Connection connection = connect(jdbc, "2.5.252"); Statement statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM " + literal(root.resolve("export-bridged.sql")) + " CHARSET 'UTF-8' FROM_1X");
            assertStable(root, connection);
        }
        for (int i = 0; i < 2; i++) {
            Class<?> type = Class.forName("org.flywaydb.core.Flyway");
            Object configuration = type.getMethod("configure").invoke(null);
            Class<?> fluent = configuration.getClass();
            fluent.getMethod("dataSource", String.class, String.class, String.class).invoke(configuration, jdbc + ";IFEXISTS=TRUE", "sa", "");
            fluent.getMethod("table", String.class).invoke(configuration, "schema_version");
            fluent.getMethod("locations", String[].class).invoke(configuration, (Object) new String[]{"classpath:db/migration"});
            fluent.getMethod("validateOnMigrate", boolean.class).invoke(configuration, true);
            fluent.getMethod("cleanDisabled", boolean.class).invoke(configuration, true);
            Object flyway = fluent.getMethod("load").invoke(configuration);
            type.getMethod("migrate").invoke(flyway);
            type.getMethod("validate").invoke(flyway);
            try (Connection connection = connect(jdbc + ";IFEXISTS=TRUE", "2.5.252")) { assertStable(root, connection); }
        }
        try (Connection connection = connect(jdbc + ";IFEXISTS=TRUE", "2.5.252")) {
            appendAndCheck(connection, "synthetic-modern-row");
        }
        System.out.println("FLYWAY13_MIGRATE_VALIDATE_RESTART_AND_ID_OK");
    }

    private static void assertStable(Path root, Connection connection) throws Exception {
        if (!Files.readString(root.resolve("expected-stable")).equals(stableSnapshot(connection)))
            throw new AssertionError("History or retained metadata fields differ across Flyway bridge");
    }

    private static String stableSnapshot(Connection connection) throws Exception {
        String history = snapshotRows(connection, "SELECT * FROM histories ORDER BY ID");
        String metadata = snapshotRows(connection, "SELECT \"installed_rank\", \"version\", \"description\", \"type\", \"script\", \"checksum\", \"installed_by\", \"installed_on\", \"execution_time\", \"success\" FROM \"schema_version\"");
        return history + metadata;
    }

    private static String snapshotRows(Connection connection, String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                    Object value = rows.getObject(i);
                    String text = value instanceof Timestamp ? ((Timestamp) value).toInstant().toString() : value == null ? null : value.toString();
                    row.append('|').append(rows.getMetaData().getColumnName(i)).append(text == null ? "null:" : text.length() + ":" + text);
                }
                values.add(row.toString());
            }
        }
        Collections.sort(values);
        return values.toString();
    }

    private static void appendAndCheck(Connection connection, String marker) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO histories (CHANGE_SUMMARY) VALUES (?)")) {
            insert.setString(1, marker); if (insert.executeUpdate() != 1) throw new AssertionError();
        }
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT MAX(ID), COUNT(*) FROM histories")) {
            rows.next();
            if (rows.getLong(1) <= HIGH_ID + 10 || rows.getInt(2) != 4) throw new AssertionError("Identity continuation");
        }
    }
    private static void assertSnapshot(Path root, Connection connection) throws Exception {
        if (!Files.readString(root.resolve("expected-rows")).equals(snapshot(connection)))
            throw new AssertionError("History rows or Flyway metadata differ");
    }
    private static String snapshot(Connection connection) throws Exception {
        List<String> values = new ArrayList<>();
        for (String sql : List.of("SELECT * FROM histories ORDER BY ID", "SELECT * FROM \"schema_version\"")) {
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
                while (rows.next()) {
                    StringBuilder row = new StringBuilder(sql);
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        row.append('|').append(rows.getMetaData().getColumnName(i));
                        Object value = rows.getObject(i);
                        String text = value instanceof Timestamp ? ((Timestamp) value).toInstant().toString()
                                : value == null ? null : value.toString();
                        row.append(text == null ? "null:" : text.length() + ":" + text);
                    }
                    values.add(row.toString());
                }
            }
        }
        Collections.sort(values);
        return values.toString();
    }
}
