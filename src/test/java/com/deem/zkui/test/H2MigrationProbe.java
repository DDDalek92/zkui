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
            case "tamper": tamper(root); break;
            case "assert-tampered": assertTampered(root); break;
            case "schema-drift": schemaDrift(root); break;
            case "hold-candidate-lock": holdCandidateLock(root); break;
            case "compete-candidate-lock": competeCandidateLock(root); break;
            case "recover-candidate-lock": recoverCandidateLock(root); break;
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
            Files.writeString(root.resolve("expected-schema"), historySchema(connection));
            assertSchemaAndConstraints(root, connection);
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
            assertSchemaAndConstraints(root, connection);
        }
        // Reopen a separate connection, check imported rows/metadata, then exercise the identity sequence.
        try (Connection connection = connect(url + ";IFEXISTS=TRUE", "2.5.252")) {
            assertSnapshot(root, connection);
            assertSchemaAndConstraints(root, connection);
            appendAndCheck(connection, "synthetic-candidate-row");
            Files.writeString(root.resolve("expected-candidate-rows"), snapshot(connection));
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
            assertSchemaAndConstraints(root, connection);
            appendAndCheck(connection, "synthetic-rollback-row");
        }
        System.out.println("OLD_DRIVER_ROLLBACK_COPY_OK");
    }


    // Flyway 4.2 contains its own version_rank -> installed_rank metadata upgrade.
    // It runs only on old-work; the original sealed database and export remain untouched.
    private static void bridge(Path root) throws Exception {
        String jdbc = url(root, "old-work") + ";IFEXISTS=TRUE";
        // Flyway4 automatically recalculates checksums while upgrading its metadata format.
        // Validate with the original engine first so that auto-conversion cannot conceal tampering.
        Class<?> original = Class.forName("com.googlecode.flyway.core.Flyway");
        Object validator = original.getConstructor().newInstance();
        original.getMethod("setDataSource", String.class, String.class, String.class).invoke(validator, jdbc, "sa", "");
        original.getMethod("validate").invoke(validator);
        Class<?> type = Class.forName("org.flywaydb.core.Flyway");
        Object flyway = type.getConstructor().newInstance();
        type.getMethod("setDataSource", String.class, String.class, String.class, String[].class)
                .invoke(flyway, jdbc, "sa", "", new String[0]);
        type.getMethod("setValidateOnMigrate", boolean.class).invoke(flyway, true);
        type.getMethod("migrate").invoke(flyway);
        try (Connection connection = connect(jdbc, "1.3.174"); Statement statement = connection.createStatement()) {
            assertStable(root, connection);
            assertSchemaAndConstraints(root, connection);
            assertConvertedChecksum(connection);
            Files.writeString(root.resolve("expected-bridge-metadata"), snapshotRows(connection, "SELECT * FROM \"schema_version\""));
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
            assertSchemaAndConstraints(root, connection);
            assertConvertedChecksum(connection);
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
            try (Connection connection = connect(jdbc + ";IFEXISTS=TRUE", "2.5.252")) {
                assertStable(root, connection);
                assertSchemaAndConstraints(root, connection);
                assertConvertedChecksum(connection);
                if (!Files.readString(root.resolve("expected-bridge-metadata")).equals(snapshotRows(connection, "SELECT * FROM \"schema_version\"")))
                    throw new AssertionError("Modern Flyway changed bridged metadata");
            }
        }
        try (Connection connection = connect(jdbc + ";IFEXISTS=TRUE", "2.5.252")) {
            appendAndCheck(connection, "synthetic-modern-row");
        }
        System.out.println("FLYWAY13_MIGRATE_VALIDATE_RESTART_AND_ID_OK");
    }

    /** Compare semantic JDBC metadata, not driver-specific sizes or generated constraint names. */
    private static String historySchema(Connection connection) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        Map<Integer, String> columns = new TreeMap<>();
        try (ResultSet rows = metadata.getColumns(null, "PUBLIC", "HISTORIES", null)) {
            while (rows.next()) {
                int type = rows.getInt("DATA_TYPE");
                String width = type == Types.VARCHAR ? ":" + rows.getInt("COLUMN_SIZE") : "";
                String value = rows.getString("COLUMN_NAME") + ":" + type + width + ":"
                        + rows.getInt("NULLABLE") + ":" + rows.getString("IS_AUTOINCREMENT");
                if (columns.put(rows.getInt("ORDINAL_POSITION"), value) != null)
                    throw new AssertionError("Duplicate column position");
            }
        }
        Map<Integer, String> primary = new TreeMap<>();
        try (ResultSet rows = metadata.getPrimaryKeys(null, "PUBLIC", "HISTORIES")) {
            while (rows.next()) {
                if (primary.put(rows.getInt("KEY_SEQ"), rows.getString("COLUMN_NAME")) != null)
                    throw new AssertionError("Duplicate primary key position");
            }
        }
        Map<String, SortedMap<Integer, String>> grouped = new HashMap<>();
        Map<String, Boolean> unique = new HashMap<>();
        try (ResultSet rows = metadata.getIndexInfo(null, "PUBLIC", "HISTORIES", false, false)) {
            while (rows.next()) {
                if (rows.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) continue;
                String name = rows.getString("INDEX_NAME");
                boolean isUnique = !rows.getBoolean("NON_UNIQUE");
                Boolean previous = unique.put(name, isUnique);
                if (previous != null && previous != isUnique) throw new AssertionError("Inconsistent index uniqueness");
                String filter = rows.getString("FILTER_CONDITION");
                // Old H2 reports no predicate as empty text; new H2 reports SQL NULL.
                String value = rows.getString("COLUMN_NAME") + ":" + rows.getString("ASC_OR_DESC")
                        + ":" + (filter == null || filter.isEmpty() ? "none" : filter);
                if (grouped.computeIfAbsent(name, ignored -> new TreeMap<>())
                        .put(rows.getInt("ORDINAL_POSITION"), value) != null)
                    throw new AssertionError("Duplicate index position");
            }
        }
        List<String> indexes = new ArrayList<>();
        grouped.forEach((name, fields) -> indexes.add(unique.get(name) + ":" + fields));
        Collections.sort(indexes);
        String shape = columns + "|" + primary + "|" + indexes;
        String expected = "{1=ID:-5:0:YES, 2=CHANGE_USER:12:100:1:NO, 3=CHANGE_DATE:93:1:NO, "
                + "4=CHANGE_SUMMARY:12:500:1:NO, 5=CHANGE_IP:12:500:1:NO}|{1=ID}|[true:{1=ID:A:none}]";
        if (!expected.equals(shape)) throw new AssertionError("History schema drift: " + shape);
        return shape;
    }

    private static void assertSchemaAndConstraints(Path root, Connection connection) throws Exception {
        if (!Files.readString(root.resolve("expected-schema")).equals(historySchema(connection)))
            throw new AssertionError("History schema differs from original engine");
        String before = snapshot(connection);
        connection.setAutoCommit(false);
        try {
            expectInsertRefusal(connection, "23505", 41, null, null, null);
            expectInsertRefusal(connection, "22001", 42, "u".repeat(101), null, null);
            expectInsertRefusal(connection, "22001", 42, null, "s".repeat(501), null);
            expectInsertRefusal(connection, "22001", 42, null, null, "i".repeat(501));
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO histories (ID, CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP) VALUES (42, NULL, NULL, NULL, NULL)")) {
                if (insert.executeUpdate() != 1) throw new AssertionError("Nullable history columns refused");
            }
            connection.rollback();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO histories (ID, CHANGE_USER, CHANGE_SUMMARY, CHANGE_IP) VALUES (42, ?, ?, ?)")) {
                insert.setString(1, "u".repeat(100)); insert.setString(2, "s".repeat(500)); insert.setString(3, "i".repeat(500));
                if (insert.executeUpdate() != 1) throw new AssertionError("Exact VARCHAR limits refused");
            }
            connection.rollback();
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
        if (!before.equals(snapshot(connection))) throw new AssertionError("Constraint probes changed rows or migration records");
        System.out.println("HISTORY_SCHEMA_PK_INDEX_NULLABILITY_WIDTH_AND_CONSTRAINTS_OK");
    }

    private static void expectInsertRefusal(Connection connection, String state, long id,
            String user, String summary, String ip) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO histories (ID, CHANGE_USER, CHANGE_SUMMARY, CHANGE_IP) VALUES (?, ?, ?, ?)")) {
            insert.setLong(1, id); insert.setString(2, user); insert.setString(3, summary); insert.setString(4, ip);
            try {
                insert.executeUpdate();
                throw new AssertionError("Constraint did not reject synthetic insert: " + state);
            } catch (SQLException expected) {
                if (!state.equals(expected.getSQLState())) throw expected;
            } finally {
                connection.rollback();
            }
        }
    }

    private static void schemaDrift(Path root) throws Exception {
        try (Connection connection = connect(url(root, "new-legacy") + ";MODE=LEGACY;IFEXISTS=TRUE", "2.5.252");
                Statement statement = connection.createStatement()) {
            // Only the disposable imported target: prove row equality alone cannot detect widened DDL.
            String before = snapshot(connection);
            statement.execute("ALTER TABLE histories ALTER COLUMN change_summary VARCHAR(501)");
            if (!before.equals(snapshot(connection))) throw new AssertionError("Drift fixture unexpectedly changed data");
            assertSchemaAndConstraints(root, connection);
        }
        throw new AssertionError("Drifted target was accepted");
    }

    private static String candidateUrl(Path root) {
        return url(root, "new-legacy") + ";MODE=LEGACY;IFEXISTS=TRUE";
    }

    private static void assertCandidateRows(Path root, Connection connection) throws Exception {
        if (!Files.readString(root.resolve("expected-candidate-rows")).equals(snapshot(connection)))
            throw new AssertionError("Candidate committed rows or metadata changed");
    }

    private static void holdCandidateLock(Path root) throws Exception {
        try (Connection connection = connect(candidateUrl(root), "2.5.252")) {
            assertCandidateRows(root, connection);
            // Signal only after the actual candidate file is opened and all committed rows checked.
            Files.writeString(root.resolve("candidate-lock-ready"), Files.readString(root.resolve("owned-fixture")),
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
            while (System.nanoTime() < deadline) Thread.sleep(50);
            throw new AssertionError("Owned idle lock holder exceeded bounded lifetime");
        }
    }

    private static void competeCandidateLock(Path root) throws Exception {
        try (Connection ignored = connect(candidateUrl(root), "2.5.252")) {
            throw new AssertionError("Second process unexpectedly opened locked candidate");
        } catch (SQLException expected) {
            if (expected.getErrorCode() != 90020 || !"90020".equals(expected.getSQLState())) throw expected;
        }
        System.out.println("EXPECTED_CANDIDATE_FILE_LOCK_REFUSAL_90020");
    }

    private static void recoverCandidateLock(Path root) throws Exception {
        String afterWrite;
        try (Connection connection = connect(candidateUrl(root), "2.5.252")) {
            assertCandidateRows(root, connection);
            assertSchemaAndConstraints(root, connection);
            long maximum;
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT MAX(ID), COUNT(*) FROM histories")) {
                if (!rows.next() || rows.getInt(2) != 4) throw new AssertionError("Expected four committed candidate rows");
                maximum = rows.getLong(1);
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO histories (CHANGE_SUMMARY) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "synthetic-after-killed-idle-holder");
                if (insert.executeUpdate() != 1) throw new AssertionError("Recovery insert failed");
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next() || keys.getLong(1) <= maximum || keys.next())
                        throw new AssertionError("Recovery identity did not continue");
                }
            }
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM histories")) {
                if (!rows.next() || rows.getInt(1) != 5) throw new AssertionError("Recovery row missing");
            }
            afterWrite = snapshot(connection);
        }
        try (Connection connection = connect(candidateUrl(root), "2.5.252")) {
            if (!afterWrite.equals(snapshot(connection))) throw new AssertionError("Recovery restart lost committed rows");
            assertSchemaAndConstraints(root, connection);
        }
        System.out.println("KILLED_IDLE_HOLDER_COMMITTED_ROWS_METADATA_SCHEMA_ID_AND_REOPEN_OK");
    }


    private static void assertConvertedChecksum(Connection connection) throws Exception {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        try (var input = H2MigrationProbe.class.getResourceAsStream("/db/migration/V1__Core.sql")) {
            if (input == null) throw new AssertionError("Original V1 resource missing");
            try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) crc.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT \"checksum\" FROM \"schema_version\" WHERE \"version\"='1'")) {
            if (!rows.next() || rows.getInt(1) != (int) crc.getValue() || rows.next())
                throw new AssertionError("Unexpected checksum conversion");
        }
    }

    private static void tamper(Path root) throws Exception {
        try (Connection connection = connect(url(root, "old-work") + ";IFEXISTS=TRUE", "1.3.174");
                Statement statement = connection.createStatement()) {
            if (statement.executeUpdate("UPDATE \"schema_version\" SET \"checksum\"=123 WHERE \"version\"='1'") != 1)
                throw new AssertionError("Expected exactly one V1 record");
        }
    }

    private static void assertTampered(Path root) throws Exception {
        try (Connection connection = connect(url(root, "old-work") + ";IFEXISTS=TRUE", "1.3.174");
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT \"checksum\", \"version_rank\" FROM \"schema_version\" WHERE \"version\"='1'")) {
            if (!rows.next() || rows.getInt(1) != 123) throw new AssertionError("Invalid metadata was silently converted/repaired");
        }
        System.out.println("TAMPERED_METADATA_REJECTED_BEFORE_BRIDGE");
    }

    private static void assertStable(Path root, Connection connection) throws Exception {
        if (!Files.readString(root.resolve("expected-stable")).equals(stableSnapshot(connection)))
            throw new AssertionError("History or retained metadata fields differ across Flyway bridge");
    }

    private static String stableSnapshot(Connection connection) throws Exception {
        String history = snapshotRows(connection, "SELECT * FROM histories ORDER BY ID");
        String metadata = snapshotRows(connection, "SELECT \"installed_rank\", \"version\", \"description\", \"type\", \"script\", \"installed_by\", \"installed_on\", \"execution_time\", \"success\" FROM \"schema_version\"");
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
