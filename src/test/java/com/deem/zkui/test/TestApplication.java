package com.deem.zkui.test;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

/** Starts the assembled JAR against test-owned ZooKeeper and H2 resources. */
final class TestApplication extends ExternalResource {
    private final TemporaryFolder temporary = new TemporaryFolder();
    private final String name;
    private final java.util.function.Supplier<String[]> overrideProvider;
    private final String password = UUID.randomUUID().toString();
    private IsolatedZooKeeper server;
    private Process process;
    ZooKeeper observer;
    String base;

    TestApplication(String name, String... overrides) {
        this(name, () -> overrides);
    }

    TestApplication(String name, java.util.function.Supplier<String[]> overrideProvider) {
        this.name = name;
        this.overrideProvider = overrideProvider;
    }

    @Override protected void before() throws Throwable {
        try {
            temporary.create();
            server = new IsolatedZooKeeper(temporary.newFolder("zk"));
            observer = server.connect();
            create("/appconfig", "");
            create("/appconfig/hosts", "");
            int port;
            try (ServerSocket reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
                port = reservation.getLocalPort();
            }
            base = "http://127.0.0.1:" + port;
            File work = temporary.newFolder("app");
            Properties config = new Properties();
            try (InputStream in = new FileInputStream(System.getProperty("zkui.test.config"))) {
                config.load(in);
            }
            String[] overrides = overrideProvider.get();
            for (int i = 0; i < overrides.length; i += 2) {
                if (overrides[i + 1] == null) config.remove(overrides[i]);
                else config.setProperty(overrides[i], overrides[i + 1]);
            }
            config.setProperty("serverPort", Integer.toString(port));
            config.setProperty("zkServer", server.address());
            if (Boolean.parseBoolean(config.getProperty("testEnsemble", "false"))
                    || Boolean.parseBoolean(config.getProperty("testNoZooKeeper", "false"))) {
                try (ServerSocket unused = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
                    config.setProperty("zkServer", "127.0.0.1:" + unused.getLocalPort()
                            + (Boolean.parseBoolean(config.getProperty("testNoZooKeeper", "false")) ? "" : "," + server.address() + "/appconfig"));
                }
            }
            config.setProperty("env", "prod");
            config.setProperty("jdbcUrl", "jdbc:h2:" + new File(work, "history").getAbsolutePath());
            config.setProperty("userSet", "{\"users\":[" + user("test-admin", "ADMIN") + ","
                    + user("test-user", "USER") + "," + user("test-other", "OTHER") + "]}");
            config.setProperty("ldapAuth", config.getProperty("testUseLdap", "false"));
            if ("true".equals(config.getProperty("testUseLdap")) && !config.getProperty("ldapUrl").startsWith("ldap://127.0.0.1:")) {
                throw new AssertionError("LDAP tests must use their loopback fixture");
            }
            try (OutputStream out = new FileOutputStream(new File(work, "config.cfg"))) {
                config.store(out, "Isolated security regression configuration");
            }
            File log = new File("target/failsafe-reports/" + name + ".log");
            log.getParentFile().mkdirs();
            process = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                    "-Dlog4j.configuration=" + new File("target/test-classes/log4j.properties").toURI(),
                    "-jar", System.getProperty("zkui.test.jar"))
                    .directory(work).redirectErrorStream(true).redirectOutput(log).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (process.isAlive() && System.nanoTime() < deadline) {
                try {
                    if (get("/login", null).status == 200) return;
                } catch (IOException ignored) {
                    // Retry only while the isolated child process is starting.
                }
                Thread.sleep(100);
            }
            throw new AssertionError("Application did not start: "
                    + new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));
        } catch (Throwable failure) {
            after();
            throw failure;
        }
    }

    private String user(String username, String role) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}";
    }

    void create(String path, String value) throws Exception {
        observer.create(path, value.getBytes(StandardCharsets.UTF_8), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    String read(String path) throws Exception {
        return new String(observer.getData(path, false, null), StandardCharsets.UTF_8);
    }

    String login(String username) throws IOException {
        Reply reply = authenticate(username, null);
        if (reply.status != 302 || reply.cookie == null) throw new AssertionError("Test login failed");
        return reply.cookie.split(";", 2)[0];
    }

    Reply authenticate(String username, String cookie) throws IOException {
        return authenticateWithPassword(username, password, cookie);
    }

    Reply authenticateWithPassword(String username, String suppliedPassword, String cookie) throws IOException {
        Reply page = get("/login", cookie);
        String sessionCookie = cookie == null ? page.cookie.split(";", 2)[0] : cookie;
        return request("/login", "username=" + URLEncoder.encode(username, "UTF-8") + "&password=" + URLEncoder.encode(suppliedPassword, "UTF-8")
                + "&csrfToken=" + token(page.body), sessionCookie,
                "application/x-www-form-urlencoded; charset=UTF-8");
    }

    Reply get(String path, String cookie) throws IOException {
        return request(path, null, cookie, null);
    }

    Reply upload(String contents, String cookie) throws IOException {
        return multipart("--zkui-test\r\nContent-Disposition: form-data; name=\"scmOverwrite\"\r\n\r\ntrue\r\n"
                + "--zkui-test\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test-import.txt\"\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\n" + contents + "\r\n--zkui-test--\r\n", cookie);
    }

    Reply multipart(String body, String cookie) throws IOException {
        if (cookie != null) body = "--zkui-test\r\nContent-Disposition: form-data; name=\"csrfToken\"\r\n\r\n"
                + token(get("/login", cookie).body) + "\r\n" + body;
        return request("/import", body, cookie, "multipart/form-data; boundary=zkui-test");
    }

    String zkAddress() { return server.address(); }

    java.util.Set<Long> sessions() { return server.sessions(); }

    String logContents() throws IOException {
        return new String(Files.readAllBytes(new File("target/failsafe-reports/" + name + ".log").toPath()), StandardCharsets.UTF_8);
    }

    static String token(String html) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("name=\"csrfToken\" value=\"([A-Za-z0-9_-]+)\"").matcher(html);
        if (!match.find()) throw new AssertionError("CSRF form token missing");
        return match.group(1);
    }

    Reply post(String path, String body, String cookie) throws IOException {
        return request(path, body + "&csrfToken=" + token(get("/login", cookie).body), cookie,
                "application/x-www-form-urlencoded; charset=UTF-8");
    }

    Reply request(String path, String body, String cookie, String contentType) throws IOException {
        return requestWithToken(path, body, cookie, contentType, null);
    }

    Reply requestWithToken(String path, String body, String cookie, String contentType, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        if (cookie != null) connection.setRequestProperty("Cookie", cookie);
        if (token != null) connection.setRequestProperty("X-CSRF-Token", token);
        try {
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            Reply reply = new Reply();
            reply.status = connection.getResponseCode();
            reply.cookie = connection.getHeaderField("Set-Cookie");
            reply.location = connection.getHeaderField("Location");
            reply.cacheControl = connection.getHeaderField("Cache-Control");
            InputStream stream = reply.status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream != null) {
                try (InputStream in = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    byte[] bytes = new byte[4096];
                    int count;
                    while ((count = in.read(bytes)) != -1) buffer.write(bytes, 0, count);
                    reply.body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return reply;
        } finally {
            connection.disconnect();
        }
    }

    @Override protected void after() {
        try {
            if (process != null) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("Application did not stop");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } finally {
            try {
                if (observer != null) observer.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (server != null) server.close();
                temporary.delete();
            }
        }
    }

    static final class Reply {
        int status;
        String cookie;
        String location;
        String cacheControl;
        String body = "";
    }
}
