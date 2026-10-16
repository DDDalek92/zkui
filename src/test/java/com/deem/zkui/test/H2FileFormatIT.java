package com.deem.zkui.test;

import com.deem.zkui.dao.Dao;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** All databases and subprocesses belong to this test; no external URL is accepted. */
public class H2FileFormatIT {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Properties config(Path base) {
        Properties props = new Properties();
        props.setProperty("env", "prod");
        props.setProperty("jdbcClass", "org.h2.Driver");
        props.setProperty("jdbcUrl", "jdbc:h2:" + base.toAbsolutePath());
        props.setProperty("jdbcUser", "sa");
        props.setProperty("jdbcPwd", "");
        return props;
    }

    private void createDatabase(Path base, boolean modern) throws Exception {
        Path log = base.resolveSibling(modern ? "candidate-create.log" : "old-create.log");
        Process process = new ProcessBuilder(java(), "-cp",
                new File(System.getProperty(modern ? "zkui.migration.h2.jar" : "zkui.migration.old.h2.jar")).getAbsolutePath(),
                "org.h2.tools.Shell", "-url", "jdbc:h2:" + base.toAbsolutePath(),
                "-user", "sa", "-password", "", "-sql",
                "CREATE TABLE owned_evidence(id BIGINT PRIMARY KEY, value_text VARCHAR(100)); "
                        + "INSERT INTO owned_evidence VALUES(9007199254740993, 'preserve-me'); "
                        + "SELECT id, value_text FROM owned_evidence")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue("Owned candidate creation must finish", process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(Files.readString(log), 0, process.exitValue());
            assertTrue(Files.readString(log), Files.readString(log).contains("9007199254740993"));
            assertTrue(Files.readString(log), Files.readString(log).contains("preserve-me"));
            assertTrue(Files.isRegularFile(Path.of(base + (modern ? ".mv.db" : ".h2.db"))));
        } finally { stop(process); }
    }

    private byte[] hash(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }

    @Test public void isolatedCandidateGuardRefusesOldFileWithoutOpeningOrCreatingAnything() throws Exception {
        Path work = temporary.newFolder().toPath();
        Path base = work.resolve("history");
        createDatabase(base, false);
        Files.writeString(work.resolve("owned-fixture"), "owned-format-guard");
        byte[] before = hash(Path.of(base + ".h2.db"));
        String classpath = new File(System.getProperty("zkui.migration.h2.jar")).getAbsolutePath()
                + File.pathSeparator + new File("target/classes").getAbsolutePath()
                + File.pathSeparator + new File("target/test-classes").getAbsolutePath();
        Path log = work.resolve("candidate-guard.log");
        Process process = new ProcessBuilder(java(), "-cp", classpath,
                "com.deem.zkui.dao.H2FileFormatGuardProbe", work.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(Files.readString(log), 0, process.exitValue());
            assertTrue(Files.readString(log).contains("CANDIDATE_GUARD_REFUSED_OLD_FILE_WITHOUT_CONNECTION"));
        } finally { stop(process); }
        assertArrayEquals(before, hash(Path.of(base + ".h2.db")));
        assertFalse(Files.exists(Path.of(base + ".mv.db")));
        assertFalse(Files.exists(work.resolve("fresh.mv.db")));
    }

    @Test public void modernRuntimeMustNotSilentlyCreateAnEmptyDatabaseBesideOldData() throws Exception {
        Path base = temporary.newFolder().toPath().resolve("history");
        createDatabase(base, false);
        byte[] before = hash(Path.of(base + ".h2.db"));
        Properties props = config(base);
        // Include an ordinary URL setting: the guard must inspect the database base, not the full URL.
        props.setProperty("jdbcUrl", "jdbc:h2:file:" + base.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE");
        try {
            new Dao(props).checkNCreate();
            fail("A different on-disk format must fail before creating an empty history database");
        } catch (IllegalStateException expected) {
            assertEquals("History database initialization failed; HTTP server was not started", expected.getMessage());
            assertNull(expected.getCause());
        }
        assertFalse("Never create a parallel empty modern database", Files.exists(Path.of(base + ".mv.db")));
        assertArrayEquals(before, hash(Path.of(base + ".h2.db")));
        // Direct DAO access must not bypass the same preflight either.
        try { new Dao(props).fetchHistoryRecords(); fail("Read must be refused"); }
        catch (IllegalStateException expected) { assertEquals("History query failed", expected.getMessage()); }
        assertFalse(Files.exists(Path.of(base + ".mv.db")));
    }

    @Test public void isolatedGenuineOldDriverStillRefusesModernFileAndStorageSwitch() throws Exception {
        Path work = temporary.newFolder().toPath();
        Path base = work.resolve("history");
        createDatabase(base, true);
        Files.writeString(work.resolve("owned-fixture"), "owned-format-guard");
        byte[] before = hash(Path.of(base + ".mv.db"));
        String classpath = new File(System.getProperty("zkui.migration.old.h2.jar")).getAbsolutePath()
                + File.pathSeparator + new File("target/classes").getAbsolutePath()
                + File.pathSeparator + new File("target/test-classes").getAbsolutePath();
        Path log = work.resolve("legacy-guard.log");
        Process process = new ProcessBuilder(java(), "-cp", classpath,
                "com.deem.zkui.dao.H2FileFormatGuardProbe", work.toString(), "legacy")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(Files.readString(log), 0, process.exitValue());
            assertTrue(Files.readString(log).contains("LEGACY_GUARD_REFUSED_MODERN_FILE_WITHOUT_CONNECTION"));
            assertTrue(Files.readString(log).contains("LEGACY_MV_STORE_SETTING_REFUSED_WITHOUT_CONNECTION"));
        } finally { stop(process); }
        assertArrayEquals(before, hash(Path.of(base + ".mv.db")));
        assertFalse(Files.exists(Path.of(base + ".h2.db")));
        assertFalse(Files.exists(work.resolve("fresh.h2.db")));
    }

    @Test public void packagedDevStartupRefusesAmbiguousFormatsBeforeCleanOrHttp() throws Exception {
        Path work = temporary.newFolder().toPath();
        Path base = work.resolve("history");
        Properties props = config(base);
        createDatabase(base, false);
        createDatabase(base, true);
        byte[] oldBefore = hash(Path.of(base + ".h2.db"));
        byte[] modernBefore = hash(Path.of(base + ".mv.db"));
        Properties application = new Properties();
        try (InputStream input = new FileInputStream(System.getProperty("zkui.test.config"))) { application.load(input); }
        application.putAll(props);
        application.setProperty("env", "dev");
        application.setProperty("userSet", "{\"users\":[{\"username\":\"format-fixture\",\"password\":\"synthetic-only\",\"role\":\"ADMIN\"}]}");
        application.setProperty("jdbcUrl", "jdbc:h2:./history");
        int port;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        application.setProperty("serverPort", Integer.toString(port));
        try (OutputStream out = Files.newOutputStream(work.resolve("config.cfg"))) { application.store(out, "Owned fixture"); }
        Path log = work.resolve("startup.log");
        ProcessBuilder builder = new ProcessBuilder(java(), "-Dlog4j.configuration="
                + new File("target/test-classes/log4j.properties").toURI(), "-jar",
                new File(System.getProperty("zkui.test.jar")).getAbsolutePath())
                .directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        for (String name : new String[]{"ZK_SERVER", "USER_SET", "LOGIN_MESSAGE", "ZKUI_BASEURL"}) builder.environment().remove(name);
        Process process = builder.start();
        try {
            assertTrue("Refused startup must exit", process.waitFor(20, TimeUnit.SECONDS));
            assertNotEquals(0, process.exitValue());
            String output = Files.readString(log);
            assertTrue(output, output.contains("History database initialization failed"));
            try (Socket socket = new Socket()) {
                try { socket.connect(new InetSocketAddress("127.0.0.1", port), 1000); fail("HTTP must not start"); }
                catch (ConnectException expected) { }
            }
        } finally { stop(process); }
        assertArrayEquals(oldBefore, hash(Path.of(base + ".h2.db")));
        assertArrayEquals(modernBefore, hash(Path.of(base + ".mv.db")));
    }

    private String java() { return new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(); }
    private void stop(Process process) throws Exception {
        if (process.isAlive()) { process.destroyForcibly(); assertTrue(process.waitFor(5, TimeUnit.SECONDS)); }
    }
}
