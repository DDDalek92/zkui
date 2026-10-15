package com.deem.zkui.test;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import static org.junit.Assert.*;

public class ImportLimitsIT {
    private static HttpServer source = source();
    private static ExecutorService executor = Executors.newCachedThreadPool();
    private static final AtomicInteger unexpected = new AtomicInteger();
    private static String base = "http://127.0.0.1:" + source.getAddress().getPort();
    private static TestApplication app = new TestApplication("import-limits", "remoteImportEnabled", "true",
            "remoteImportAllowedUrls", base + "/good|" + base + "/redirect|" + base + "/large|" + base + "/slow");
    private static TestApplication disabled = new TestApplication("import-disabled");
    @ClassRule public static RuleChain resources = RuleChain.outerRule(new ExternalResource() {
        @Override protected void before() {
            source.setExecutor(executor);
            source.createContext("/", exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    if ("/redirect".equals(path)) {
                        exchange.getResponseHeaders().set("Location", base + "/unexpected");
                        exchange.sendResponseHeaders(302, -1);
                    } else if ("/large".equals(path)) exchange.sendResponseHeaders(200, 2 * 1024 * 1024);
                    else if ("/slow".equals(path)) {
                        try { Thread.sleep(3500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        exchange.sendResponseHeaders(200, -1);
                    } else {
                        if (!"/good".equals(path)) unexpected.incrementAndGet();
                        byte[] body = "/appconfig=remote-key=中文".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                    }
                } finally { exchange.close(); }
            });
            source.start();
        }
        @Override protected void after() { source.stop(0); executor.shutdownNow(); }
    }).around(app).around(disabled);

    private static HttpServer source() {
        try { return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private TestApplication.Reply remote(TestApplication target, String url) throws Exception {
        String body = "--zkui-test\r\nContent-Disposition: form-data; name=\"remoteUrl\"\r\n\r\n"
                + url + "\r\n--zkui-test--\r\n";
        return target.multipart(body, target.login("test-admin"));
    }

    @Test public void onlyApprovedRemoteUrlCanImport() throws Exception {
        assertEquals(302, remote(app, base + "/good").status);
        assertEquals("中文", app.read("/appconfig/remote-key"));
        assertEquals(400, remote(disabled, base + "/good").status);
        assertEquals(400, remote(app, base + "/unexpected").status);
        assertEquals(0, unexpected.get());
    }

    @Test public void redirectsAndLargeRemoteResponsesAreRejected() throws Exception {
        assertEquals(400, remote(app, base + "/redirect").status);
        assertEquals(413, remote(app, base + "/large").status);
        assertEquals(0, unexpected.get());
    }

    @Test(timeout = 10000) public void remoteReadTimesOut() throws Exception {
        assertEquals(400, remote(app, base + "/slow").status);
    }

    @Test public void rejectsLargeUploads() throws Exception {
        StringBuilder content = new StringBuilder("/appconfig=too-large=");
        while (content.length() <= 1024 * 1024) content.append('x');
        assertEquals(413, app.upload(content.toString(), app.login("test-admin")).status);
        assertNull(app.observer.exists("/appconfig/too-large", false));
    }

    @Test public void validatesWholeFileBeforeWritingAndProtectsSystemPaths() throws Exception {
        String cookie = app.login("test-admin");
        assertEquals(400, app.upload("/appconfig=not-partial=first\ninvalid-record", cookie).status);
        assertNull(app.observer.exists("/appconfig/not-partial", false));
        assertEquals(400, app.upload("/zookeeper=import-denied=value", cookie).status);
        assertNull(app.observer.exists("/zookeeper/import-denied", false));
        assertEquals(400, app.upload("-/", cookie).status);
    }

    @Test public void rejectsExcessivePartsAndMultipleFiles() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 33; i++) body.append("--zkui-test\r\nContent-Disposition: form-data; name=\"field")
                .append(i).append("\"\r\n\r\nvalue\r\n");
        body.append("--zkui-test--\r\n");
        assertEquals(400, app.multipart(body.toString(), app.login("test-admin")).status);
        String file = "--zkui-test\r\nContent-Disposition: form-data; name=\"file\"; filename=\"file.txt\"\r\n\r\n"
                + "/appconfig=multi-file-denied=value\r\n";
        assertEquals(400, app.multipart(file + file + "--zkui-test--\r\n", app.login("test-admin")).status);
        assertNull(app.observer.exists("/appconfig/multi-file-denied", false));
    }
}
