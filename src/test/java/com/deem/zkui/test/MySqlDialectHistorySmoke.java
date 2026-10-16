package com.deem.zkui.test;

import com.deem.zkui.dao.Dao;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Independent old MySQL-dialect history copies; fixed CI-owned endpoints only. */
public final class MySqlDialectHistorySmoke {
    private static final String LOCATION = "db/mysql-migration";
    private static final String RESOURCE = "/db/mysql-migration/V1__Core.sql";
    private static final Path ORIGINAL = Path.of("target/mysql-dialect-original-snapshot.txt");
    private static final Path BRIDGED = Path.of("target/mysql-dialect-bridged-snapshot.txt");
    private static final String RETAINED = "SELECT installed_rank, version, description, type, script, installed_by, installed_on, execution_time, success FROM schema_version ORDER BY installed_rank";

    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("ZKUI_OWNED_MYSQL_FIXTURE"))) throw new AssertionError("Owned CI fixture required");
        if (args.length != 1) throw new AssertionError("One fixed mode required");
        switch (args[0]) {
            case "seed": MySqlMigrationSmoke.seedDialect(); System.out.println("MYSQL56_DIALECT_FLYWAY2_GENUINE_HISTORY_SEEDED"); break;
            case "bridge": bridge(); break;
            case "verify": verify(); break;
            default: throw new AssertionError("Unknown fixture mode");
        }
    }

    private static Properties config(String database, boolean old) {
        if (!Set.of("zkui_dialect_source", "zkui_dialect_copy", "zkui_dialect_bad").contains(database)) throw new AssertionError();
        Properties p = new Properties();
        p.setProperty("env", "prod"); p.setProperty("jdbcClass", "com.mysql.cj.jdbc.Driver");
        p.setProperty("jdbcUser", "root"); p.setProperty("jdbcPwd", "synthetic-mysql-ci-only");
        p.setProperty("jdbcUrl", "jdbc:mysql://127.0.0.1:" + (old ? "13306" : "13307") + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&connectTimeout=5000&socketTimeout=5000"
                + (old ? "&useSSL=false" : "&sslMode=VERIFY_IDENTITY"));
        return p;
    }

    private static Connection connect(String database, boolean old) throws Exception {
        Properties p = config(database, old); Class.forName(p.getProperty("jdbcClass"));
        Connection c = DriverManager.getConnection(p.getProperty("jdbcUrl"), p.getProperty("jdbcUser"), p.getProperty("jdbcPwd"));
        if (!c.getMetaData().getDatabaseProductVersion().startsWith(old ? "5.6." : "8.4.")
                || !c.getMetaData().getDriverVersion().contains(old ? "8.0.16" : "26.7.0")) {
            c.close(); throw new AssertionError("Fixture engine mismatch");
        }
        if (!old) try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SHOW STATUS LIKE 'Ssl_cipher'")) {
            require(r.next() && !r.getString(2).isEmpty(), "Verified TLS required");
        }
        return c;
    }

    private static void bridge() throws Exception {
        String original = Files.readString(ORIGINAL);
        try (Connection c = connect("zkui_dialect_source", true)) { require(original.equals(MySqlMigrationSmoke.snapshot(c)), "Source changed before bridge"); }
        try (Connection c = connect("zkui_dialect_bad", true); Statement s = c.createStatement()) {
            require(original.equals(MySqlMigrationSmoke.snapshot(c)), "Damaged-copy baseline mismatch");
            require(s.executeUpdate("UPDATE schema_version SET checksum=123 WHERE version='1'") == 1, "Missing V1");
        }
        String damaged;
        try (Connection c = connect("zkui_dialect_bad", true)) { damaged = MySqlMigrationSmoke.snapshot(c); }
        try { validatedBridge("zkui_dialect_bad"); throw new AssertionError("Bad dialect checksum accepted"); }
        catch (InvocationTargetException expected) { require(expected.getCause().getClass().getName().startsWith("com.googlecode.flyway."), "Must fail in original validation"); }
        try (Connection c = connect("zkui_dialect_bad", true)) {
            require(MySqlFlywayBridgeSmoke.hasVersionRank(c) && damaged.equals(MySqlMigrationSmoke.snapshot(c)), "Refusal converted or erased evidence");
        }
        String rows, retained;
        try (Connection c = connect("zkui_dialect_copy", true)) {
            require(original.equals(MySqlMigrationSmoke.snapshot(c)) && MySqlFlywayBridgeSmoke.hasVersionRank(c), "Normal-copy baseline mismatch");
            require(MySqlFlywayBridgeSmoke.checksum(c) == MySqlFlywayBridgeSmoke.checksum(false, RESOURCE), "Wrong original dialect checksum");
            rows = MySqlMigrationSmoke.rows(c, "SELECT * FROM histories ORDER BY id");
            retained = MySqlMigrationSmoke.rows(c, RETAINED);
        }
        validatedBridge("zkui_dialect_copy");
        String converted;
        try (Connection c = connect("zkui_dialect_copy", true)) {
            require(!MySqlFlywayBridgeSmoke.hasVersionRank(c), "Bridge did not convert format");
            require(rows.equals(MySqlMigrationSmoke.rows(c, "SELECT * FROM histories ORDER BY id"))
                    && retained.equals(MySqlMigrationSmoke.rows(c, RETAINED)), "Bridge changed rows or retained metadata");
            require(MySqlFlywayBridgeSmoke.checksum(c) == MySqlFlywayBridgeSmoke.checksum(true, RESOURCE), "Wrong converted dialect checksum");
            converted = MySqlMigrationSmoke.snapshot(c);
        }
        validatedBridge("zkui_dialect_copy");
        try (Connection c = connect("zkui_dialect_copy", true)) { require(converted.equals(MySqlMigrationSmoke.snapshot(c)), "Repeated bridge changed data"); }
        Files.writeString(BRIDGED, converted);
        try (Connection c = connect("zkui_dialect_source", true)) { require(original.equals(MySqlMigrationSmoke.snapshot(c)), "Sealed source changed"); }
        System.out.println("MYSQL56_DIALECT_BRIDGE_BAD_CHECKSUM_REFUSED_ROWS_FIELDS_REPEAT_SOURCE_PRESERVED_OK");
    }

    private static void validatedBridge(String database) throws Exception {
        Properties p = config(database, true);
        Class<?> oldType = Class.forName("com.googlecode.flyway.core.Flyway");
        Object old = oldType.getConstructor().newInstance();
        oldType.getMethod("setDataSource", String.class, String.class, String.class)
                .invoke(old, p.getProperty("jdbcUrl"), p.getProperty("jdbcUser"), p.getProperty("jdbcPwd"));
        oldType.getMethod("setLocations", String[].class).invoke(old, (Object) new String[]{LOCATION});
        try (Connection c = connect(database, true)) { if (MySqlFlywayBridgeSmoke.hasVersionRank(c)) oldType.getMethod("validate").invoke(old); }
        Class<?> type = Class.forName("org.flywaydb.core.Flyway"); Object bridge = type.getConstructor().newInstance();
        type.getMethod("setDataSource", String.class, String.class, String.class, String[].class)
                .invoke(bridge, p.getProperty("jdbcUrl"), p.getProperty("jdbcUser"), p.getProperty("jdbcPwd"), new String[0]);
        type.getMethod("setLocations", String[].class).invoke(bridge, (Object) new String[]{LOCATION});
        type.getMethod("setValidateOnMigrate", boolean.class).invoke(bridge, true);
        type.getMethod("migrate").invoke(bridge);
    }

    private static void verify() throws Exception {
        String original = Files.readString(ORIGINAL);
        Properties unbridged = config("zkui_dialect_source", false); unbridged.setProperty("env", "dev");
        try (Connection c = connect("zkui_dialect_source", false)) { require(original.equals(MySqlMigrationSmoke.snapshot(c)), "Unbridged logical restore changed data"); }
        try { new Dao(unbridged).checkNCreate(); throw new AssertionError("Unbridged dialect accepted"); }
        catch (IllegalStateException expected) { require(expected.getCause() == null, "Expected sanitized refusal"); }
        MySqlMigrationSmoke.packagedHistoryPage(unbridged, true);
        try (Connection c = connect("zkui_dialect_source", false)) { require(original.equals(MySqlMigrationSmoke.snapshot(c)), "Unbridged refusal changed old metadata"); }
        Properties target = config("zkui_dialect_copy", false);
        String expected = Files.readString(BRIDGED);
        try (Connection c = connect("zkui_dialect_copy", false)) { require(expected.equals(MySqlMigrationSmoke.snapshot(c)), "Bridged logical restore changed data"); }
        Dao dao = new Dao(target); dao.checkNCreate(); target.setProperty("env", "dev"); new Dao(target).checkNCreate();
        try (Connection c = connect("zkui_dialect_copy", false)) { require(expected.equals(MySqlMigrationSmoke.snapshot(c)), "Modern bootstrap changed dialect history"); }
        require(dao.fetchHistoryRecords().size() == 3, "Missing old rows");
        dao.insertHistory("dialect-ci", "127.0.0.1", "mysql-modern-added");
        require(dao.fetchHistoryRecords().get(0).getId() > 9007199254741003L, "Dialect identity did not continue");
        String after;
        try (Connection c = connect("zkui_dialect_copy", false)) { after = MySqlMigrationSmoke.snapshot(c); }
        MySqlMigrationSmoke.packagedHistoryPage(target, false); MySqlMigrationSmoke.packagedHistoryPage(target, false);
        try (Connection c = connect("zkui_dialect_copy", false)) { require(after.equals(MySqlMigrationSmoke.snapshot(c)), "Packaged Main restart changed rows/metadata"); }
        System.out.println("MYSQL84_TLS_OLD_DIALECT_UNBRIDGED_REFUSED_BRIDGED_APP_MAIN_RESTART_IDS_DEV_PRESERVED_OK");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
