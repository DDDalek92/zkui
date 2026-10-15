package com.deem.zkui.test;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Exercises the actual assembled jar, its servlet discovery and its temporary H2 database. */
public class PackagedApplicationIT {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 60000)
    public void logsInPerformsCrudAndLogsOut() throws Exception {
        try (IsolatedZooKeeper server = new IsolatedZooKeeper(temporary.newFolder("zk"));
             ZooKeeper observer = server.connect()) {
            observer.create("/appconfig", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            observer.create("/appconfig/hosts", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            int port;
            try (ServerSocket reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
                port = reservation.getLocalPort();
            }
            File work = temporary.newFolder("app");
            Properties config = new Properties();
            try (InputStream in = new FileInputStream(System.getProperty("zkui.test.config"))) {
                config.load(in);
            }
            String password = UUID.randomUUID().toString();
            config.setProperty("serverPort", Integer.toString(port));
            config.setProperty("zkServer", server.address());
            config.setProperty("env", "prod");
            config.setProperty("jdbcUrl", "jdbc:h2:" + new File(work, "history").getAbsolutePath());
            config.setProperty("userSet", "{\"users\":[{\"username\":\"test-admin\",\"password\":\""
                    + password + "\",\"role\":\"ADMIN\"}]}");
            config.setProperty("ldapAuth", "false");
            try (OutputStream out = new FileOutputStream(new File(work, "config.cfg"))) {
                config.store(out, "Isolated test configuration; no external services");
            }
            File log = new File("target/failsafe-reports/packaged-application.log");
            log.getParentFile().mkdirs();
            String java = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath();
            String logConfig = new File("target/test-classes/log4j.properties").toURI().toString();
            Process app = new ProcessBuilder(java, "-Dlog4j.configuration=" + logConfig,
                    "-jar", System.getProperty("zkui.test.jar"))
                    .directory(work).redirectErrorStream(true).redirectOutput(log).start();
            try {
                String base = "http://127.0.0.1:" + port;
                awaitLogin(base, app, log);
                Reply anonymous = request(base + "/home", null, null);
                assertEquals(302, anonymous.status);
                assertEquals("/login", new URL(base + "/home").toURI().resolve(anonymous.location).getPath());
                Reply loginPage = request(base + "/login", null, null);
                Reply login = request(base + "/login", "username=test-admin&password=" + password
                        + "&csrfToken=" + TestApplication.token(loginPage.body), loginPage.cookie.split(";", 2)[0]);
                assertEquals(302, login.status);
                assertNotNull("Login must create a session cookie", login.cookie);
                String cookie = login.cookie.split(";", 2)[0];
                Reply home = request(base + "/home", null, cookie);
                assertEquals(200, home.status);
                assertTrue("Home must render the ZooKeeper tree", home.body.contains("/home?zkPath=%2Fappconfig"));

                String create = "action=Save+Property&searchStr=&currentPath=%2Fappconfig%2F&displayPath=%2Fappconfig"
                        + "&newProperty=smoke-key&newValue=smoke-value&csrfToken=" + TestApplication.token(home.body);
                assertEquals(302, request(base + "/home", create, cookie).status);
                assertEquals("smoke-value", new String(observer.getData("/appconfig/smoke-key", false, null), StandardCharsets.UTF_8));
                String update = create.replace("Save+Property", "Update+Property").replace("smoke-value", "updated-value");
                assertEquals(302, request(base + "/home", update, cookie).status);
                assertEquals("updated-value", new String(observer.getData("/appconfig/smoke-key", false, null), StandardCharsets.UTF_8));
                Reply exported = request(base + "/export?zkPath=%2Fappconfig", null, cookie);
                assertEquals(200, exported.status);
                assertTrue(exported.body.contains("smoke-key=updated-value"));
                Reply history = request(base + "/history", null, cookie);
                assertEquals(200, history.status);
                assertTrue("History must persist through instrumented ActiveJDBC model", history.body.contains("smoke-key"));
                String delete = "action=Delete&searchStr=&currentPath=%2Fappconfig%2F&displayPath=%2Fappconfig"
                        + "&propChkGroup=%2Fappconfig%2Fsmoke-key&csrfToken=" + TestApplication.token(home.body);
                assertEquals(302, request(base + "/home", delete, cookie).status);
                assertNull(observer.exists("/appconfig/smoke-key", false));
                assertEquals(302, request(base + "/logout", "csrfToken=" + TestApplication.token(home.body), cookie).status);
                assertEquals(302, request(base + "/home", null, cookie).status);
            } finally {
                app.destroy();
                if (!app.waitFor(5, TimeUnit.SECONDS)) {
                    app.destroyForcibly();
                    assertTrue("Application process must stop", app.waitFor(5, TimeUnit.SECONDS));
                }
            }
        }
    }

    private void awaitLogin(String base, Process app, File log) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (!app.isAlive()) break;
            try {
                if (request(base + "/login", null, null).status == 200) return;
            } catch (IOException ignored) {
                // Retry only while the child process is starting.
            }
            Thread.sleep(100);
        }
        fail("Packaged application did not become ready. Log: "
                + new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));
    }

    private Reply request(String address, String form, String cookie) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        if (cookie != null) connection.setRequestProperty("Cookie", cookie);
        try {
            if (form != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(form.getBytes(StandardCharsets.UTF_8));
                }
            }
            Reply reply = new Reply();
            reply.status = connection.getResponseCode();
            reply.cookie = connection.getHeaderField("Set-Cookie");
            reply.location = connection.getHeaderField("Location");
            InputStream stream = reply.status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream != null) {
                try (InputStream in = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    byte[] bytes = new byte[4096];
                    int length;
                    while ((length = in.read(bytes)) != -1) buffer.write(bytes, 0, length);
                    reply.body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return reply;
        } finally {
            connection.disconnect();
        }
    }

    private static class Reply {
        int status;
        String cookie;
        String location;
        String body = "";
    }
}
