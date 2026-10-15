package com.deem.zkui.test;

import com.deem.zkui.dao.Dao;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Only owned temporary databases and child JVMs; never accepts an external JDBC URL. */
public class DatabaseStartupIT {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Properties config(File work) throws Exception {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(System.getProperty("zkui.test.config"))) { props.load(in); }
        props.setProperty("env", "prod");
        props.setProperty("https", "false");
        props.setProperty("baseURL", "/");
        props.setProperty("zkServer", "127.0.0.1:1"); // No ZK request is needed to serve the login page.
        props.setProperty("jdbcClass", "org.h2.Driver");
        props.setProperty("jdbcUrl", "jdbc:h2:" + new File(work, "history").getAbsolutePath());
        props.setProperty("jdbcUser", "sa");
        props.setProperty("jdbcPwd", UUID.randomUUID().toString());
        props.setProperty("userSet", "{\"users\":[]}");
        return props;
    }

    private Connection connect(Properties props) throws Exception {
        Class.forName("org.h2.Driver");
        return DriverManager.getConnection(props.getProperty("jdbcUrl"), props.getProperty("jdbcUser"), props.getProperty("jdbcPwd"));
    }

    private void launch(File work, Properties props, boolean ready) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) { port = reservation.getLocalPort(); }
        props.setProperty("serverPort", Integer.toString(port));
        try (OutputStream out = new FileOutputStream(new File(work, "config.cfg"))) { props.store(out, "Owned synthetic fixture"); }
        File log = new File(work, "startup-" + UUID.randomUUID() + ".log");
        ProcessBuilder builder = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                "-Dlog4j.configuration=" + new File("target/test-classes/log4j.properties").toURI(),
                "-jar", System.getProperty("zkui.test.jar")).directory(work).redirectErrorStream(true).redirectOutput(log);
        for (String key : new String[]{"ZK_SERVER", "USER_SET", "LOGIN_MESSAGE", "ZKUI_BASEURL"}) builder.environment().remove(key);
        Process child = builder.start();
        try {
            if (ready) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                boolean served = false;
                while (child.isAlive() && System.nanoTime() < deadline) {
                    HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/login").openConnection();
                    connection.setConnectTimeout(200);
                    connection.setReadTimeout(200);
                    try { if (connection.getResponseCode() == 200) { served = true; break; } }
                    catch (IOException ignored) { }
                    finally { connection.disconnect(); }
                    Thread.sleep(50);
                }
                assertTrue("Valid owned database must allow login readiness", served);
            } else {
                assertTrue("Migration failure must terminate rather than serve login", child.waitFor(20, TimeUnit.SECONDS));
                assertNotEquals("Failed migration must exit nonzero", 0, child.exitValue());
                String output = Files.readString(log.toPath(), StandardCharsets.UTF_8);
                assertTrue(output, output.contains("Database initialization failed; server was not started"));
                assertFalse("Do not expose the synthetic JDBC password", output.contains(props.getProperty("jdbcPwd")));
                assertFalse(output, output.contains("Started ServerConnector"));
                try (ServerSocket free = new ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"))) { assertEquals(port, free.getLocalPort()); }
            }
        } finally {
            child.destroy();
            if (!child.waitFor(5, TimeUnit.SECONDS)) { child.destroyForcibly(); assertTrue(child.waitFor(5, TimeUnit.SECONDS)); }
        }
    }

    @Test public void wrongPasswordStopsBeforeHttpAndPreservesHistory() throws Exception {
        File work = temporary.newFolder();
        Properties props = config(work);
        new Dao(props).checkNCreate();
        new Dao(props).insertHistory("synthetic", "127.0.0.1", "preserved");
        String password = props.getProperty("jdbcPwd");
        props.setProperty("jdbcPwd", UUID.randomUUID().toString());
        launch(work, props, false);
        props.setProperty("jdbcPwd", password);
        assertEquals("preserved", new Dao(props).fetchHistoryRecords().get(0).getChangeSummary());
    }

    @Test public void nonemptyUnversionedSchemaIsNotImplicitlyBaselinedOrCleaned() throws Exception {
        File work = temporary.newFolder();
        Properties props = config(work);
        try (Connection db = connect(props); Statement sql = db.createStatement()) {
            sql.execute("CREATE TABLE owned_marker (value VARCHAR(40))");
            sql.execute("INSERT INTO owned_marker VALUES ('unchanged')");
        }
        launch(work, props, false);
        try (Connection db = connect(props); Statement sql = db.createStatement(); ResultSet rows = sql.executeQuery("SELECT value FROM owned_marker")) {
            assertTrue(rows.next()); assertEquals("unchanged", rows.getString(1)); assertFalse(rows.next());
        }
    }

    @Test public void missingHistoryTableStopsEvenWhenFlywayReportsV1Applied() throws Exception {
        File work = temporary.newFolder();
        Properties props = config(work);
        new Dao(props).checkNCreate();
        try (Connection db = connect(props); Statement sql = db.createStatement()) { sql.execute("DROP TABLE histories"); }
        launch(work, props, false);
        try (Connection db = connect(props); Statement sql = db.createStatement(); ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM \"schema_version\" WHERE \"version\" = '1' AND \"success\" = TRUE")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
    }

    @Test public void freshAndExistingDatabaseStartWithoutWipingHistory() throws Exception {
        File work = temporary.newFolder();
        Properties props = config(work);
        launch(work, props, true);
        new Dao(props).insertHistory("synthetic", "127.0.0.1", "survives-restart");
        launch(work, props, true);
        assertEquals(1, new Dao(props).fetchHistoryRecords().size());
        assertEquals("survives-restart", new Dao(props).fetchHistoryRecords().get(0).getChangeSummary());
    }
}
