package com.deem.zkui.test;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StartupSecurityIT {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void sampleCredentialsRefuseBeforeDatabaseOrHttp() throws Exception { refused(false); }
    @Test public void forwardingWithoutTrustedPeersRefusesBeforeDatabaseOrHttp() throws Exception { refused(true); }

    private void refused(boolean proxy) throws Exception {
        Path work = temporary.newFolder().toPath();
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(System.getProperty("zkui.test.config"))) { p.load(in); }
        p.setProperty("jdbcUrl", "jdbc:h2:./must-not-create");
        p.setProperty("ldapAuth", "false");
        p.setProperty("allowSampleUsers", "false");
        p.setProperty("userSet", proxy ? "{\"users\":[{\"username\":\"test\",\"password\":\"synthetic-only\",\"role\":\"ADMIN\"}]}"
                : "{\"users\":[{\"username\":\"admin\",\"password\":\"manager\",\"role\":\"ADMIN\"}]}");
        p.setProperty("X-Forwarded-For", Boolean.toString(proxy));
        p.remove("trustedProxyAddresses");
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) { port = reservation.getLocalPort(); }
        p.setProperty("serverPort", Integer.toString(port));
        try (OutputStream out = Files.newOutputStream(work.resolve("config.cfg"))) { p.store(out, "Owned startup guard fixture"); }
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-jar",
                Path.of(System.getProperty("zkui.test.jar")).toAbsolutePath().toString()).directory(work.toFile())
                .redirectErrorStream(true).redirectOutput(work.resolve("app.log").toFile());
        for (String name : new String[]{"ZK_SERVER", "USER_SET", "LOGIN_MESSAGE", "ZKUI_BASEURL"}) builder.environment().remove(name);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            assertNotEquals(0, process.exitValue());
            String log = Files.readString(work.resolve("app.log"));
            assertTrue(log, log.contains(proxy ? "Forwarded headers require explicit" : "Configure unique nonempty web credentials"));
            assertFalse(log.contains("\"password\""));
            assertFalse(Files.exists(work.resolve("must-not-create.mv.db")));
            try (Socket socket = new Socket()) {
                assertThrows(ConnectException.class, () -> socket.connect(new InetSocketAddress("127.0.0.1", port), 1000));
            }
        } finally {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); assertTrue(process.waitFor(5, TimeUnit.SECONDS)); }
        }
    }
}
