package com.deem.zkui.test;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static com.deem.zkui.test.H2MigrationProbe.*;

/** Bounded, test-owned append-only audit transfer. Not a production migration or interchange API. */
final class H2AuditDeltaProbe {
    private static final long BASE_MAX = HIGH_ID + 10;
    private static final String HISTORY = "SELECT * FROM histories ORDER BY ID";
    private static final String METADATA = "SELECT * FROM \"schema_version\"";
    private record Row(long id, String user, Timestamp date, String summary, String ip) { }

    static void run(String mode, Path root, String nonce) throws Exception {
        switch (mode) {
            case "delta-prepare":
                try (Connection connection = candidate(root); PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO histories (CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, "差量😀"); insert.setTimestamp(2, Timestamp.from(Instant.parse("2026-01-02T03:04:05.123Z")));
                    insert.setString(3, "  <script>synthetic-delta</script>\nquote' slash\\  "); insert.setString(4, "");
                    if (insert.executeUpdate() != 1) throw new AssertionError("Candidate append failed");
                }
                exportDelta(root, nonce); break;
            case "delta-export": exportDelta(root, nonce); break;
            case "delta-restore": restoreDelta(root, nonce); break;
            case "delta-conflict": conflictDelta(root, nonce); break;
            case "delta-change-base": case "delta-delete-base":
                try (Connection connection = candidate(root); Statement statement = connection.createStatement()) {
                    String sql = mode.equals("delta-change-base")
                            ? "UPDATE histories SET CHANGE_SUMMARY='synthetic-changed-base' WHERE ID=41" : "DELETE FROM histories WHERE ID=41";
                    if (statement.executeUpdate(sql) != 1) throw new AssertionError("Expected one synthetic base row");
                }
                break;
            default: throw new AssertionError("Unknown owned delta mode");
        }
    }

    private static Connection candidate(Path root) throws Exception {
        return connect(url(root, "new-legacy") + ";MODE=LEGACY;IFEXISTS=TRUE", "2.5.252");
    }
    private static String digest(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static void assertBase(Path root, Connection connection) throws Exception {
        String original = snapshotRows(connection, "SELECT * FROM histories WHERE ID <= " + BASE_MAX + " ORDER BY ID");
        if (!Files.readString(root.resolve("expected-base-history")).equals(original))
            throw new IllegalStateException("Append-only audit baseline changed or deleted");
        if (!Files.readString(root.resolve("expected-base-metadata")).equals(snapshotRows(connection, METADATA)))
            throw new IllegalStateException("Original migration metadata changed");
    }
    private static Row row(ResultSet result) throws Exception {
        return new Row(result.getLong("ID"), result.getString("CHANGE_USER"), result.getTimestamp("CHANGE_DATE"),
                result.getString("CHANGE_SUMMARY"), result.getString("CHANGE_IP"));
    }
    private static void writeNullable(DataOutputStream output, String value) throws Exception {
        output.writeBoolean(value != null);
        if (value != null) output.writeUTF(value); // Explicit test-only Java modified-UTF format; NULL is not empty.
    }
    private static String readNullable(DataInputStream input) throws Exception {
        return input.readBoolean() ? input.readUTF() : null;
    }
    private static void exportDelta(Path root, String nonce) throws Exception {
        try (Connection connection = candidate(root)) {
            assertBase(root, connection);
            List<Row> delta = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT * FROM histories WHERE ID > ? ORDER BY ID")) {
                select.setLong(1, BASE_MAX);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        if (delta.size() >= 1000) throw new AssertionError("Owned delta row budget exceeded");
                        delta.add(row(rows));
                    }
                }
            }
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(root.resolve("delta.bin"), StandardOpenOption.CREATE_NEW))) {
                output.writeUTF("ZKUI_OWNED_AUDIT_DELTA_V1"); output.writeUTF(nonce);
                output.writeUTF(digest(root.resolve("expected-rows"))); output.writeInt(delta.size());
                for (Row row : delta) {
                    output.writeLong(row.id); writeNullable(output, row.user);
                    output.writeBoolean(row.date != null);
                    if (row.date != null) { output.writeLong(row.date.toInstant().getEpochSecond()); output.writeInt(row.date.getNanos()); }
                    writeNullable(output, row.summary); writeNullable(output, row.ip);
                }
            }
            Files.writeString(root.resolve("delta.sha256"), digest(root.resolve("delta.bin")), StandardOpenOption.CREATE_NEW);
            Files.writeString(root.resolve("expected-delta-history"), snapshotRows(connection, HISTORY), StandardOpenOption.CREATE_NEW);
        }
        System.out.println("APPEND_ONLY_AUDIT_DELTA_EXPORTED_WITH_BASELINE_AND_PAYLOAD_HASH");
    }
    private static List<Row> readDelta(Path root, String nonce) throws Exception {
        Path file = root.resolve("delta.bin");
        if (Files.size(file) > 1024 * 1024 || !Files.readString(root.resolve("delta.sha256")).equals(digest(file)))
            throw new IllegalStateException("Owned delta payload integrity failure");
        List<Row> rows = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            if (!"ZKUI_OWNED_AUDIT_DELTA_V1".equals(input.readUTF()) || !nonce.equals(input.readUTF())
                    || !digest(root.resolve("expected-rows")).equals(input.readUTF()))
                throw new IllegalStateException("Owned delta baseline mismatch");
            int count = input.readInt();
            if (count < 0 || count > 1000) throw new IllegalStateException("Owned delta row budget exceeded");
            long previous = BASE_MAX;
            for (int i = 0; i < count; i++) {
                long id = input.readLong(); String user = readNullable(input); Timestamp date = null;
                if (input.readBoolean()) {
                    long seconds = input.readLong(); int nanos = input.readInt();
                    if (nanos < 0 || nanos > 999999999) throw new IllegalStateException("Invalid owned timestamp");
                    date = Timestamp.from(Instant.ofEpochSecond(seconds, nanos));
                }
                String summary = readNullable(input), ip = readNullable(input);
                if (id <= previous || (user != null && user.length() > 100) || (summary != null && summary.length() > 500)
                        || (ip != null && ip.length() > 500)) throw new IllegalStateException("Invalid owned delta row");
                rows.add(new Row(id, user, date, summary, ip)); previous = id;
            }
            if (input.read() != -1) throw new IllegalStateException("Trailing owned delta data");
        }
        return rows;
    }
    private static void bind(PreparedStatement insert, Row row) throws Exception {
        insert.setLong(1, row.id); insert.setString(2, row.user); insert.setTimestamp(3, row.date);
        insert.setString(4, row.summary); insert.setString(5, row.ip);
    }
    private static void applyDelta(Path root, Connection connection, List<Row> delta) throws Exception {
        assertBase(root, connection);
        connection.setAutoCommit(false);
        try (PreparedStatement find = connection.prepareStatement("SELECT * FROM histories WHERE ID=?");
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO histories (ID, CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP) VALUES (?, ?, ?, ?, ?)")) {
            for (Row row : delta) {
                find.setLong(1, row.id);
                try (ResultSet existing = find.executeQuery()) {
                    if (existing.next()) {
                        if (!row.equals(row(existing)) || existing.next()) throw new IllegalStateException("Conflicting audit delta row");
                        continue; // Exactly matching repeated rows are accepted, never overwritten.
                    }
                }
                bind(insert, row);
                if (insert.executeUpdate() != 1) throw new AssertionError("Delta insert failed");
            }
            assertBase(root, connection);
            if (!Files.readString(root.resolve("expected-delta-history")).equals(snapshotRows(connection, HISTORY)))
                throw new IllegalStateException("Recovered audit snapshot differs before commit");
            connection.commit();
        } catch (Exception | AssertionError ex) {
            connection.rollback(); throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }
    private static void restoreDelta(Path root, String nonce) throws Exception {
        List<Row> delta = readDelta(root, nonce);
        copyClosedDatabase(root.resolve("sealed-source"), root.resolve("delta-rollback"));
        String jdbc = url(root, "delta-rollback") + ";IFEXISTS=TRUE";
        String after;
        try (Connection connection = connect(jdbc, "1.3.174")) {
            assertSnapshot(root, connection); applyDelta(root, connection, delta);
            String first = snapshot(connection); applyDelta(root, connection, delta);
            if (!first.equals(snapshot(connection))) throw new AssertionError("Repeated delta replay changed rows");
            assertSchemaAndConstraints(root, connection);
            long maximum;
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT MAX(ID) FROM histories")) {
                rows.next(); maximum = rows.getLong(1);
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO histories (CHANGE_SUMMARY) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "synthetic-old-engine-after-delta");
                if (insert.executeUpdate() != 1) throw new AssertionError("Post-recovery insert failed");
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next() || keys.getLong(1) <= maximum || keys.next()) throw new AssertionError("Post-delta identity collision");
                }
            }
            after = snapshot(connection);
        }
        try (Connection connection = connect(jdbc, "1.3.174")) {
            if (!after.equals(snapshot(connection))) throw new AssertionError("Old restart lost recovered rows");
            assertBase(root, connection);
        }
        System.out.println("AUDIT_DELTA_OLD_COPY_EXACT_REPLAY_IDEMPOTENCE_ID_AND_RESTART_OK");
    }
    private static void conflictDelta(Path root, String nonce) throws Exception {
        List<Row> delta = readDelta(root, nonce);
        if (delta.size() != 2) throw new AssertionError("Expected two synthetic delta rows");
        copyClosedDatabase(root.resolve("sealed-source"), root.resolve("delta-conflict"));
        try (Connection connection = connect(url(root, "delta-conflict") + ";IFEXISTS=TRUE", "1.3.174");
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO histories (ID, CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP) VALUES (?, ?, ?, ?, ?)")) {
            Row second = delta.get(1);
            bind(insert, new Row(second.id, second.user, second.date, "synthetic-conflict-not-to-overwrite", second.ip));
            if (insert.executeUpdate() != 1) throw new AssertionError("Conflict fixture failed");
            String before = snapshot(connection);
            try {
                applyDelta(root, connection, delta); // First row inserts; second conflicts: whole transaction must roll back.
                throw new AssertionError("Conflicting delta was accepted");
            } catch (IllegalStateException expected) {
                if (!"Conflicting audit delta row".equals(expected.getMessage())) throw expected;
            }
            if (!before.equals(snapshot(connection))) throw new AssertionError("Conflict left partial inserts or overwrote data");
        }
        System.out.println("AUDIT_DELTA_CONFLICT_REFUSED_ENTIRE_TRANSACTION_ROLLED_BACK");
    }
}
