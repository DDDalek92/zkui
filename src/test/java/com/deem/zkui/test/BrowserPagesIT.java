package com.deem.zkui.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

/** Render real templates for the browser checks, with isolated, synthetic data. */
public class BrowserPagesIT {
    @ClassRule public static TestApplication app = new TestApplication("browser-pages", "baseURL", "/zkui/");

    @Test public void renderBrowserFixturesFromPackagedApplication() throws Exception {
        app.create("/appconfig/sort", "parent\n  中文");
        app.create("/appconfig/sort/Beta", "sort-fixture equal");
        app.create("/appconfig/sort/alpha", "sort-fixture equal");
        app.create("/appconfig/sort/gamma", "sort-fixture\n  <script>window.valueExecuted=true</script>");
        app.create("/appconfig/sort/db.Password", "synthetic-browser-secret");
        String cookie = app.login("test-admin");
        Path directory = Paths.get("target/browser-pages");
        Files.createDirectories(directory);
        save(directory.resolve("home.html"), app.get("/home?zkPath=/appconfig/sort", cookie));
        save(directory.resolve("search.html"), app.post("/home",
                "action=Search&displayPath=/appconfig/sort&searchStr=sort-fixture", cookie));
        save(directory.resolve("readonly.html"), app.get("/home?zkPath=/appconfig/sort", app.login("test-user")));
    }

    private void save(Path path, TestApplication.Reply reply) throws Exception {
        assertEquals(200, reply.status);
        assertTrue(reply.body.contains("data-sortable=\"true\""));
        assertTrue(reply.body.contains("js/table-sort.js"));
        assertFalse(reply.body.contains("unpkg.com"));
        Files.write(path, reply.body.getBytes(StandardCharsets.UTF_8));
    }
}
