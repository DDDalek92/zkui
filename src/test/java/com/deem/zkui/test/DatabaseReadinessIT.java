package com.deem.zkui.test;

import com.deem.zkui.dao.Dao;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DatabaseReadinessIT {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Properties config() throws Exception {
        Properties props = new Properties();
        props.setProperty("env", "prod");
        props.setProperty("jdbcClass", "org.h2.Driver");
        props.setProperty("jdbcUrl", "jdbc:h2:" + temporary.newFolder().getAbsolutePath() + "/history");
        props.setProperty("jdbcUser", "sa");
        props.setProperty("jdbcPwd", "");
        return props;
    }

    private void assertRefused(Properties props) {
        try {
            new Dao(props).checkNCreate();
            fail("Unusable history database must prevent readiness");
        } catch (IllegalStateException expected) {
            assertEquals("History database initialization failed; HTTP server was not started", expected.getMessage());
            assertNull("Driver messages may contain credentials", expected.getCause());
        }
    }

    @Test public void missingHistoryTableCannotBeHiddenByAnAppliedMigrationRecord() throws Exception {
        Properties props = config();
        Dao dao = new Dao(props);
        dao.checkNCreate();
        try (Connection connection = DriverManager.getConnection(props.getProperty("jdbcUrl"), "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE HISTORIES");
        }
        assertRefused(props);
    }

    @Test public void changedMigrationChecksumIsRejectedWithoutRepairOrDataLoss() throws Exception {
        Properties props = config();
        Dao dao = new Dao(props);
        dao.checkNCreate();
        dao.insertHistory("synthetic-user", "127.0.0.1", "preserved-row");
        try (Connection connection = DriverManager.getConnection(props.getProperty("jdbcUrl"), "sa", "");
                Statement statement = connection.createStatement()) {
            assertEquals(1, statement.executeUpdate("UPDATE \"schema_version\" SET \"checksum\" = 123 WHERE \"version\" = '1'"));
        }
        assertRefused(props);
        try (Connection connection = DriverManager.getConnection(props.getProperty("jdbcUrl"), "sa", "");
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT \"checksum\" FROM \"schema_version\" WHERE \"version\" = '1'")) {
            assertTrue(rows.next());
            assertEquals(123, rows.getInt(1));
        }
        assertEquals("preserved-row", dao.fetchHistoryRecords().get(0).getChangeSummary());
    }

    @Test public void invalidDatabaseMakesThePackagedProcessExitBeforeHttpStarts() throws Exception {
        File work = temporary.newFolder();
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(System.getProperty("zkui.test.config"))) { props.load(in); }
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            port = reservation.getLocalPort();
        }
        String marker = "synthetic-secret-not-for-output";
        props.setProperty("serverPort", Integer.toString(port));
        props.setProperty("jdbcUrl", "jdbc:unsupported:" + marker);
        props.setProperty("jdbcPwd", marker);
        props.setProperty("env", "prod");
        props.setProperty("userSet", "{\"users\":[{\"username\":\"readiness-fixture\",\"password\":\"synthetic-only\",\"role\":\"ADMIN\"}]}");
        try (OutputStream out = new FileOutputStream(new File(work, "config.cfg"))) { props.store(out, "Owned fixture"); }
        File log = new File(work, "startup.log");
        ProcessBuilder builder = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                "-Dlog4j.configuration=" + new File("target/test-classes/log4j.properties").toURI(),
                "-jar", new File(System.getProperty("zkui.test.jar")).getAbsolutePath())
                .directory(work).redirectErrorStream(true).redirectOutput(log);
        for (String name : new String[]{"ZK_SERVER", "USER_SET", "LOGIN_MESSAGE", "ZKUI_BASEURL"}) builder.environment().remove(name);
        Process process = builder.start();
        try {
            assertTrue("Startup must fail promptly", process.waitFor(20, TimeUnit.SECONDS));
            assertNotEquals(0, process.exitValue());
            String output = Files.readString(log.toPath(), StandardCharsets.UTF_8);
            assertTrue(output, output.contains("History database initialization failed"));
            assertFalse("Credentials must not escape through exception causes", output.contains(marker));
            try (Socket socket = new Socket()) {
                try {
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 1000);
                    fail("HTTP listener must not start after migration failure");
                } catch (ConnectException expected) { }
            }
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); assertTrue(process.waitFor(5, TimeUnit.SECONDS)); }
        }
    }
}
