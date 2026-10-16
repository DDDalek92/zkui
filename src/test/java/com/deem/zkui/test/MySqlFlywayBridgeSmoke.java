package com.deem.zkui.test;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.CRC32;

/** CI-owned database copies only. No arbitrary host, URL, credentials or schema accepted. */
public final class MySqlFlywayBridgeSmoke {
    private static final String PASSWORD = "synthetic-mysql-ci-only";
    private static final String ROWS = "SELECT id, change_user, change_date, change_summary, change_ip FROM histories ORDER BY id";
    private static final String RETAINED = "SELECT installed_rank, version, description, type, script, installed_by, installed_on, execution_time, success FROM schema_version ORDER BY installed_rank";
    private static final String ALL = "SELECT * FROM schema_version ORDER BY installed_rank";
    private static final Path EVIDENCE = Path.of("target/mysql-flyway-bridge-snapshot.txt");

    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("ZKUI_OWNED_MYSQL_FIXTURE"))) throw new AssertionError("Owned CI fixture required");
        if (args.length != 1) throw new AssertionError("One fixed fixture mode required");
        if ("bridge".equals(args[0])) bridge();
        else if ("modern".equals(args[0])) modern();
        else throw new AssertionError("Unknown fixture mode");
    }

    private static String url(String database, boolean old) {
        if (!Set.of("zkui_fixture", "zkui_bridge", "zkui_bridge_bad", "zkui_modern_fresh").contains(database)) throw new AssertionError();
        return "jdbc:mysql://127.0.0.1:" + (old ? "13306" : "13307") + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&connectTimeout=5000&socketTimeout=5000"
                + (old ? "&useSSL=false" : "&sslMode=VERIFY_IDENTITY");
    }

    private static Connection connect(String database, boolean old) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection connection = DriverManager.getConnection(url(database, old), "root", PASSWORD);
        String server = connection.getMetaData().getDatabaseProductVersion();
        String driver = connection.getMetaData().getDriverVersion();
        if (!(server.startsWith(old ? "5.6." : "8.4.") && driver.contains(old ? "8.0.16" : "26.7.0"))) {
            connection.close(); throw new AssertionError("Fixture engine mismatch");
        }
        if (!old) {
            try (Statement statement = connection.createStatement(); ResultSet tls = statement.executeQuery("SHOW STATUS LIKE 'Ssl_cipher'")) {
                require(tls.next() && !tls.getString(2).isEmpty(), "Verified TLS required");
            }
        }
        return connection;
    }

    private static void bridge() throws Exception {
        String sealed;
        try (Connection c = connect("zkui_fixture", true)) { sealed = snapshot(c, ROWS) + snapshot(c, ALL); }
        // A deliberately damaged COPY must fail original-engine validation before Flyway4 can
        // auto-recalculate its checksum as part of its supported metadata-format conversion.
        try (Connection c = connect("zkui_bridge_bad", true); Statement s = c.createStatement()) {
            require(hasVersionRank(c), "Expected genuine Flyway2 metadata");
            require(s.executeUpdate("UPDATE schema_version SET checksum=123 WHERE version='1'") == 1, "Missing fixture V1");
        }
        String badBefore;
        try (Connection c = connect("zkui_bridge_bad", true)) { badBefore = snapshot(c, ROWS) + snapshot(c, ALL); }
        try { validatedBridge("zkui_bridge_bad"); throw new AssertionError("Damaged checksum accepted"); }
        catch (InvocationTargetException expected) {
            require(expected.getCause().getClass().getName().startsWith("com.googlecode.flyway."), "Must fail in original validation");
        }
        try (Connection c = connect("zkui_bridge_bad", true)) {
            require(hasVersionRank(c) && badBefore.equals(snapshot(c, ROWS) + snapshot(c, ALL)), "Rejected copy was converted or modified");
        }
        System.out.println("MYSQL_FLYWAY2_BAD_CHECKSUM_REFUSED_BEFORE_BRIDGE_NO_METADATA_CHANGE");

        String rows, retained;
        try (Connection c = connect("zkui_bridge", true)) {
            require(hasVersionRank(c), "Expected original Flyway2 format");
            require(checksum(c) == checksum(false, "/db/migration/V1__Core.sql"), "Original raw checksum mismatch");
            rows = snapshot(c, ROWS); retained = snapshot(c, RETAINED);
        }
        validatedBridge("zkui_bridge");
        String converted;
        try (Connection c = connect("zkui_bridge", true)) {
            require(!hasVersionRank(c), "Supported bridge did not upgrade metadata");
            require(rows.equals(snapshot(c, ROWS)) && retained.equals(snapshot(c, RETAINED)), "Bridge changed history or retained fields");
            require(checksum(c) == checksum(true, "/db/migration/V1__Core.sql"), "Expected normalized checksum");
            converted = snapshot(c, ROWS) + snapshot(c, ALL);
        }
        validatedBridge("zkui_bridge");
        try (Connection c = connect("zkui_bridge", true)) { require(converted.equals(snapshot(c, ROWS) + snapshot(c, ALL)), "Repeated bridge changed data"); }
        Files.writeString(EVIDENCE, converted, StandardCharsets.UTF_8);
        try (Connection c = connect("zkui_bridge", true)) { Files.writeString(Path.of("target/mysql-bridge-app-snapshot.txt"), MySqlMigrationSmoke.snapshot(c)); }
        try (Connection c = connect("zkui_fixture", true)) { require(sealed.equals(snapshot(c, ROWS) + snapshot(c, ALL)), "Original database changed"); }
        System.out.println("MYSQL56_FLYWAY4_SUPPORTED_COPY_BRIDGE_ROWS_RETAINED_FIELDS_CHECKSUM_REPEAT_OK");
    }

    private static void validatedBridge(String database) throws Exception {
        Class<?> original = Class.forName("com.googlecode.flyway.core.Flyway");
        Object old = original.getConstructor().newInstance();
        original.getMethod("setDataSource", String.class, String.class, String.class).invoke(old, url(database, true), "root", PASSWORD);
        // Only an untouched Flyway2-format copy can be validated by Flyway2; a repeated bridge
        // instead uses Flyway4's validate-on-migrate against its already converted record.
        try (Connection c = connect(database, true)) {
            if (hasVersionRank(c)) original.getMethod("validate").invoke(old);
        }
        Class<?> type = Class.forName("org.flywaydb.core.Flyway");
        Object bridge = type.getConstructor().newInstance();
        type.getMethod("setDataSource", String.class, String.class, String.class, String[].class)
                .invoke(bridge, url(database, true), "root", PASSWORD, new String[0]);
        type.getMethod("setValidateOnMigrate", boolean.class).invoke(bridge, true);
        type.getMethod("migrate").invoke(bridge);
    }

    private static void modern() throws Exception {
        // This is an engine/module rehearsal, NOT the application's MySQL bootstrap.
        require(Class.forName("org.flywaydb.database.mysql.MySQLDatabase") != null, "MySQL module missing");
        String expected = Files.readString(EVIDENCE, StandardCharsets.UTF_8);
        String afterInsert;
        try (Connection c = connect("zkui_bridge", false)) {
            require(!hasVersionRank(c), "Unbridged metadata is not an accepted modern input");
            require(expected.equals(snapshot(c, ROWS) + snapshot(c, ALL)), "Logical transfer changed bridge snapshot");
        }
        for (int i = 0; i < 2; i++) {
            org.flywaydb.core.Flyway flyway = modernFlyway("zkui_bridge", "db/migration");
            flyway.migrate(); flyway.validate();
        }
        try (Connection c = connect("zkui_bridge", false)) {
            require(expected.equals(snapshot(c, ROWS) + snapshot(c, ALL)), "Modern Flyway changed bridged rows or metadata");
            try (Statement s = c.createStatement()) {
                require(s.executeUpdate("INSERT INTO histories(change_summary) VALUES('modern-engine-owned-audit')") == 1, "Insert failed");
                try (ResultSet r = s.executeQuery("SELECT MAX(id), COUNT(*) FROM histories")) {
                    require(r.next() && r.getLong(1) > 9007199254741003L && r.getInt(2) == 4, "Identity did not continue");
                }
            }
            afterInsert = snapshot(c, ROWS) + snapshot(c, ALL);
        }
        modernFlyway("zkui_bridge", "db/migration").migrate();
        try (Connection c = connect("zkui_bridge", false)) { require(afterInsert.equals(snapshot(c, ROWS) + snapshot(c, ALL)), "Reopen lost appended row or changed metadata"); }
        org.flywaydb.core.Flyway fresh = modernFlyway("zkui_modern_fresh", "db/mysql-migration");
        fresh.migrate(); fresh.validate();
        try (Connection c = connect("zkui_modern_fresh", false); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO histories(change_summary) VALUES('modern-engine-fresh')");
        }
        modernFlyway("zkui_modern_fresh", "db/mysql-migration").migrate();
        try (Connection c = connect("zkui_modern_fresh", false); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM histories")) {
            require(r.next() && r.getInt(1) == 1, "Modern fresh restart lost row");
            require(checksum(c) == checksum(true, "/db/mysql-migration/V1__Core.sql"), "Fresh normalized checksum mismatch");
        }
        String damaged;
        try (Connection c = connect("zkui_modern_fresh", false); Statement s = c.createStatement()) {
            require(s.executeUpdate("UPDATE schema_version SET checksum=123 WHERE version='1'") == 1, "Missing fresh V1");
            damaged = snapshot(c, ROWS) + snapshot(c, ALL);
        }
        try { modernFlyway("zkui_modern_fresh", "db/mysql-migration").migrate(); throw new AssertionError("Modern engine accepted damaged checksum"); }
        catch (org.flywaydb.core.api.FlywayException expectedFailure) { /* No repair or baseline. */ }
        try (Connection c = connect("zkui_modern_fresh", false)) { require(damaged.equals(snapshot(c, ROWS) + snapshot(c, ALL)), "Modern refusal changed evidence"); }
        System.out.println("MYSQL84_FLYWAY13_BAD_CHECKSUM_REFUSED_NO_REPAIR_OR_DATA_LOSS");
        System.out.println("MYSQL84_TLS_FLYWAY13_MODULE_BRIDGED_METADATA_REPEAT_ID_FRESH_ENGINE_OK_NOT_APP_BOOTSTRAP");
    }

    private static org.flywaydb.core.Flyway modernFlyway(String database, String location) {
        return org.flywaydb.core.Flyway.configure().dataSource(url(database, false), "root", PASSWORD)
                .table("schema_version").schemas(database).defaultSchema(database).locations(location)
                .cleanDisabled(true).baselineOnMigrate(false).validateOnMigrate(true).load();
    }

    static boolean hasVersionRank(Connection c) throws Exception {
        try (ResultSet columns = c.getMetaData().getColumns(c.getCatalog(), null, "schema_version", "version_rank")) { return columns.next(); }
    }

    static int checksum(Connection c) throws Exception {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT checksum FROM schema_version WHERE version='1'")) {
            require(r.next(), "Missing V1"); int checksum = r.getInt(1);
            require(!r.wasNull() && !r.next(), "Invalid V1"); return checksum;
        }
    }

    static int checksum(boolean normalized, String resource) throws Exception {
        try (InputStream in = MySqlFlywayBridgeSmoke.class.getResourceAsStream(resource)) {
            require(in != null, "Missing migration resource"); CRC32 crc = new CRC32();
            if (!normalized) crc.update(in.readAllBytes());
            else try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return (int) crc.getValue();
        }
    }

    private static String snapshot(Connection c, String sql) throws Exception {
        StringBuilder result = new StringBuilder(sql).append('\n');
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) {
                for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
                    Object value;
                    if (r.getMetaData().getColumnType(i) == Types.TIMESTAMP) {
                        Timestamp t = r.getTimestamp(i, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                        value = t == null ? null : t.toInstant();
                    } else if ("success".equalsIgnoreCase(r.getMetaData().getColumnName(i))) {
                        value = r.getBoolean(i); if (r.wasNull()) value = null;
                    } else value = r.getObject(i);
                    String text = value == null ? null : value.toString();
                    result.append(text == null ? "|null:" : "|" + text.length() + ":" + text);
                }
                result.append('\n');
            }
        }
        return result.toString();
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
