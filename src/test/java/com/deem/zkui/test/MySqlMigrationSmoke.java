package com.deem.zkui.test;

import com.deem.zkui.dao.Dao;
import com.deem.zkui.domain.History;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Run only by the owned Linux CI MySQL job; no caller-supplied URLs or databases. */
public final class MySqlMigrationSmoke {
    private static final long HIGH_ID = 9007199254740993L;
    private static final String TEXT = "  中文😀\nquote' slash\\  ";
    private static final String PASSWORD = "synthetic-mysql-ci-only";

    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("ZKUI_OWNED_MYSQL_FIXTURE"))) throw new AssertionError("Owned CI fixture required");
        if ("seed".equals(args[0])) seed();
        else if ("unbridged".equals(args[0])) unbridged();
        else if ("verify".equals(args[0])) verify();
        else throw new AssertionError("Unknown fixture mode");
    }

    private static Properties config(String database, boolean seed) {
        if (!Set.of("zkui_fixture", "zkui_app_bridge", "zkui_fresh", "zkui_unknown", "zkui_future", "zkui_drift", "zkui_dialect_source").contains(database)) throw new AssertionError();
        Properties props = new Properties();
        props.setProperty("env", "prod");
        props.setProperty("jdbcClass", "com.mysql.cj.jdbc.Driver");
        props.setProperty("jdbcUser", "root");
        props.setProperty("jdbcPwd", PASSWORD);
        props.setProperty("jdbcUrl", "jdbc:mysql://127.0.0.1:" + (seed ? "13306" : "13307") + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&connectTimeout=5000&socketTimeout=5000"
                + (seed ? "&useSSL=false" : "&sslMode=VERIFY_IDENTITY"));
        return props;
    }

    private static Connection connect(Properties props) throws Exception {
        Class.forName(props.getProperty("jdbcClass"));
        return DriverManager.getConnection(props.getProperty("jdbcUrl"), props.getProperty("jdbcUser"), props.getProperty("jdbcPwd"));
    }

    private static void seed() throws Exception {
        seed("zkui_fixture", "db/migration", "target/mysql-original-snapshot.txt");
        System.out.println("MYSQL56_ORIGINAL_V1_ROWS_AND_METADATA_SEEDED");
    }

    static void seedDialect() throws Exception {
        seed("zkui_dialect_source", "db/mysql-migration", "target/mysql-dialect-original-snapshot.txt");
    }

    private static void seed(String database, String location, String evidence) throws Exception {
        Properties props = config(database, true);
        try (Connection connection = connect(props)) {
            require(connection.getMetaData().getDatabaseProductVersion().startsWith("5.6."), "Old fixture server mismatch");
            require(connection.getMetaData().getDriverVersion().contains("8.0.16"), "Old fixture driver mismatch");
        }
        Class<?> type = Class.forName("com.googlecode.flyway.core.Flyway");
        Object flyway = type.getConstructor().newInstance();
        type.getMethod("setDataSource", String.class, String.class, String.class).invoke(flyway, props.getProperty("jdbcUrl"), "root", PASSWORD);
        type.getMethod("setLocations", String[].class).invoke(flyway, (Object) new String[]{location});
        type.getMethod("setValidateOnMigrate", boolean.class).invoke(flyway, true);
        type.getMethod("migrate").invoke(flyway); // Genuine isolated original engine and V1.
        try (Connection connection = connect(props);
                PreparedStatement insert = connection.prepareStatement("INSERT INTO histories (ID, CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP) VALUES (?, ?, ?, ?, ?)")) {
            insert.setLong(1, 41); insert.setString(2, null); insert.setTimestamp(3, null);
            insert.setString(4, ""); insert.setString(5, ""); insert.executeUpdate();
            insert.setLong(1, HIGH_ID); insert.setString(2, "旧用户");
            insert.setTimestamp(3, new Timestamp(1700000000000L)); insert.setString(4, TEXT); insert.setString(5, "127.0.0.1"); insert.executeUpdate();
            insert.setLong(1, HIGH_ID + 10); insert.setString(4, "x".repeat(500)); insert.executeUpdate();
            Files.writeString(Path.of(evidence), snapshot(connection));
        }
    }

    static String snapshot(Connection connection) throws Exception {
        return rows(connection, "SELECT * FROM histories ORDER BY id") + rows(connection, "SELECT * FROM schema_version ORDER BY installed_rank");
    }

    static String rows(Connection connection, String sql) throws Exception {
        StringBuilder result = new StringBuilder(sql);
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                    int type = rows.getMetaData().getColumnType(i);
                    Object value;
                    if (type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE) {
                        Timestamp timestamp = rows.getTimestamp(i, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                        value = timestamp == null ? null : timestamp.toInstant().toString();
                    } else if ("success".equalsIgnoreCase(rows.getMetaData().getColumnName(i))) {
                        value = rows.getBoolean(i); if (rows.wasNull()) value = null;
                    } else value = rows.getObject(i);
                    String text = value == null ? null : value.toString();
                    result.append(text == null ? "|null:" : "|" + text.length() + ":" + text);
                }
                result.append('\n');
            }
        }
        return result.toString();
    }

    private static void verify() throws Exception {
        Properties restored = config("zkui_app_bridge", false);
        String expected = Files.readString(Path.of("target/mysql-bridge-app-snapshot.txt"));
        Dao dao = new Dao(restored);
        try (Connection connection = connect(restored)) {
            require(connection.getMetaData().getDriverVersion().contains("26.7.0"), "Modern driver mismatch");
            require(connection.getMetaData().getDatabaseProductVersion().startsWith("8.4."), "Target server mismatch");
            try (Statement statement = connection.createStatement(); ResultSet cipher = statement.executeQuery("SHOW STATUS LIKE 'Ssl_cipher'")) {
                require(cipher.next() && !cipher.getString(2).isEmpty(), "Verified TLS is required");
            }
            require(expected.equals(snapshot(connection)), "Logical restore changed original rows/metadata");
        }
        dao.checkNCreate();
        restored.setProperty("env", "dev"); // Never permission to erase history on either modern backend.
        new Dao(restored).checkNCreate();
        try (Connection connection = connect(restored)) { require(expected.equals(snapshot(connection)), "Application startup changed historical data/metadata"); }
        List<History> old = dao.fetchHistoryRecords();
        require(old.size() == 3 && old.get(1).getId() == HIGH_ID && TEXT.equals(old.get(1).getChangeSummary()), "Historical values changed");
        dao.insertHistory("modern-ci", "127.0.0.1", "mysql-modern-added");
        require(dao.fetchHistoryRecords().get(0).getId() > HIGH_ID + 10, "Historical identity did not continue");
        require(old.get(1).getChangeSummary().equals(TEXT), "Detached results changed");

        Properties fresh = config("zkui_fresh", false);
        Dao newDatabase = new Dao(fresh);
        newDatabase.checkNCreate();
        newDatabase.insertHistory("fresh-ci", "127.0.0.1", "fresh-persisted");
        new Dao(fresh).checkNCreate();
        require(newDatabase.fetchHistoryRecords().size() == 1, "Fresh MySQL dialect/restart failed");
        try (Connection connection = connect(fresh);
                ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "histories", "id")) {
            require(columns.next() && columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
                    && "YES".equals(columns.getString("IS_AUTOINCREMENT")), "MySQL identity/nullability mismatch");
        }

        packagedHistoryPage(restored);
        String afterWrite;
        try (Connection c = connect(restored)) { afterWrite = snapshot(c); }
        packagedHistoryPage(restored); // Actual second Main process, same database, not DAO-only restart.
        try (Connection c = connect(restored)) { require(afterWrite.equals(snapshot(c)), "Main restart changed rows or metadata"); }
        Path untrusted = Files.createTempFile("zkui-owned-untrusted-", ".p12");
        try {
            java.security.KeyStore trust = java.security.KeyStore.getInstance("PKCS12");
            trust.load(null, "synthetic-untrusted".toCharArray());
            try (OutputStream out = Files.newOutputStream(untrusted)) { trust.store(out, "synthetic-untrusted".toCharArray()); }
            Properties rejectedTls = config("zkui_app_bridge", false);
            rejectedTls.setProperty("jdbcUrl", rejectedTls.getProperty("jdbcUrl")
                    + "&trustCertificateKeyStoreUrl=" + URLEncoder.encode(untrusted.toUri().toString(), StandardCharsets.UTF_8)
                    + "&trustCertificateKeyStorePassword=synthetic-untrusted&trustCertificateKeyStoreType=PKCS12&fallbackToSystemTrustStore=false");
            refused(rejectedTls);
        } finally { Files.delete(untrusted); }
        Properties wrongPassword = config("zkui_app_bridge", false);
        wrongPassword.setProperty("jdbcPwd", "wrong-synthetic-fixture-password");
        refused(wrongPassword);
        for (String database : List.of("zkui_future", "zkui_drift")) {
            Properties negative = config(database, false);
            try (Connection c = connect(negative); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE histories LIKE zkui_fresh.histories");
                s.execute("INSERT INTO histories SELECT * FROM zkui_fresh.histories");
                s.execute("CREATE TABLE schema_version LIKE zkui_fresh.schema_version");
                s.execute("INSERT INTO schema_version SELECT * FROM zkui_fresh.schema_version");
                if ("zkui_future".equals(database)) s.executeUpdate("INSERT INTO schema_version SELECT 2, '2', description, type, script, checksum, installed_by, installed_on, execution_time, success FROM schema_version WHERE version='1'");
                else s.execute("ALTER TABLE histories MODIFY change_summary VARCHAR(501)");
            }
            String before;
            try (Connection c = connect(negative)) { before = snapshot(c); }
            negative.setProperty("env", "dev"); refused(negative);
            try (Connection c = connect(negative)) { require(before.equals(snapshot(c)), "Refusal erased negative fixture evidence"); }
        }
        try (Connection connection = connect(fresh); Statement statement = connection.createStatement()) {
            require(statement.executeUpdate("UPDATE schema_version SET checksum=123 WHERE version='1'") == 1, "V1 fixture missing");
        }
        refused(fresh);
        require(newDatabase.fetchHistoryRecords().size() == 1, "Refusal lost existing history");
        try (Connection connection = connect(fresh); Statement statement = connection.createStatement();
                ResultSet checksum = statement.executeQuery("SELECT checksum FROM schema_version WHERE version='1'")) {
            require(checksum.next() && checksum.getInt(1) == 123, "Checksum was automatically repaired");
        }
        Properties unknown = config("zkui_unknown", false);
        try (Connection connection = connect(unknown); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE unrelated_fixture (id INT)");
        }
        refused(unknown);
        System.out.println("MYSQL84_TLS_LEGACY_RESTORE_FRESH_DIALECT_IDS_RESTART_PACKAGED_HISTORY_AND_FAILURES_OK");
        System.out.println("MYSQL84_MODERN_APP_BRIDGED_MAIN_RESTART_DEV_PRESERVE_FUTURE_SCHEMA_DRIFT_REFUSALS_OK");
    }

    private static void unbridged() throws Exception {
        Properties p = config("zkui_fixture", false);
        String before;
        try (Connection c = connect(p)) { before = snapshot(c); }
        require(before.equals(Files.readString(Path.of("target/mysql-original-snapshot.txt"))), "Original restore changed rows/metadata");
        p.setProperty("env", "dev"); refused(p); packagedHistoryPage(p, true);
        try (Connection c = connect(p)) { require(before.equals(snapshot(c)), "Unbridged refusal converted or erased original metadata"); }
        System.out.println("MYSQL84_MODERN_APP_UNBRIDGED_REFUSED_NO_CONVERSION_OR_DEV_CLEAN");
    }

    private static void refused(Properties props) {
        try { new Dao(props).checkNCreate(); throw new AssertionError("Unsafe database was accepted"); }
        catch (IllegalStateException expected) {
            require(expected.getCause() == null && expected.getMessage().contains("History database initialization failed"), "Failure must be sanitized");
        }
    }

    private static void packagedHistoryPage(Properties database) throws Exception {
        packagedHistoryPage(database, false);
    }

    static void packagedHistoryPage(Properties database, boolean expectRefusal) throws Exception {
        Path work = Files.createTempDirectory("zkui-owned-mysql-http-");
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("config.cfg"))) { props.load(in); }
        props.putAll(database);
        props.setProperty("zkServer", "127.0.0.1:1");
        props.setProperty("ldapAuth", "false");
        props.setProperty("baseURL", "/mysql-fixture");
        props.setProperty("userSet", "{\"users\":[{\"username\":\"mysql-admin\",\"password\":\"synthetic-http-only\",\"role\":\"ADMIN\"},{\"username\":\"mysql-user\",\"password\":\"synthetic-http-only\",\"role\":\"USER\"}]}");
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) { port = reservation.getLocalPort(); }
        props.setProperty("serverPort", Integer.toString(port));
        try (OutputStream out = Files.newOutputStream(work.resolve("config.cfg"))) { props.store(out, "Owned MySQL HTTP fixture"); }
        Path jar = Path.of("target/zkui-2.0-SNAPSHOT-jar-with-dependencies.jar").toAbsolutePath();
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Djavax.net.ssl.trustStore=" + System.getProperty("javax.net.ssl.trustStore"),
                "-Djavax.net.ssl.trustStorePassword=synthetic-trust-only", "-jar", jar.toString())
                .directory(work.toFile()).redirectErrorStream(true).redirectOutput(work.resolve("app.log").toFile());
        for (String name : new String[]{"ZK_SERVER", "USER_SET", "LOGIN_MESSAGE", "ZKUI_BASEURL"}) builder.environment().remove(name);
        Process process = builder.start();
        try {
            String base = "http://127.0.0.1:" + port + "/mysql-fixture";
            HttpClient admin = client();
            if (expectRefusal) {
                require(process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() != 0, "Unbridged Main must exit before HTTP");
                require(Files.readString(work.resolve("app.log")).contains("History database initialization failed"), "Expected sanitized startup refusal");
                try { get(admin, base + "/login"); throw new AssertionError("Refused Main exposed HTTP"); }
                catch (IOException expected) { /* Owned port is not listening. */ }
                return;
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            boolean ready = false;
            while (process.isAlive() && System.nanoTime() < deadline) {
                try { if (get(admin, base + "/login").statusCode() == 200) { ready = true; break; } }
                catch (IOException ignored) { }
                Thread.sleep(100);
            }
            require(ready, "Packaged MySQL application did not become ready");
            login(admin, base, "mysql-admin");
            HttpResponse<String> history = get(admin, base + "/history");
            require(history.statusCode() == 200 && history.body().contains("mysql-modern-added"), "Packaged history did not read persisted MySQL rows");
            HttpClient user = client();
            login(user, base, "mysql-user");
            require(get(user, base + "/history").statusCode() == 403, "USER must not read audit history");
        } finally {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); require(process.waitFor(5, TimeUnit.SECONDS), "Owned app did not stop"); }
            // Only this test-created directory, after the owned process has exited.
            try (var files = Files.walk(work)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static void login(HttpClient client, String base, String user) throws Exception {
        String html = get(client, base + "/login").body();
        var token = java.util.regex.Pattern.compile("name=\"csrfToken\" value=\"([A-Za-z0-9_-]+)\"").matcher(html);
        require(token.find(), "CSRF token missing");
        String body = "username=" + user + "&password=synthetic-http-only&csrfToken=" + token.group(1);
        HttpResponse<String> result = client.send(HttpRequest.newBuilder(URI.create(base + "/login")).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        require(result.statusCode() == 302, "Fixture login failed");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
