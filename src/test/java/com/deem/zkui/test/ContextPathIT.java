package com.deem.zkui.test;

import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContextPathIT {
    @ClassRule public static TestApplication app = new TestApplication("context-path", "baseURL", "/zkui/");

    private void target(TestApplication.Reply reply, String path) throws Exception {
        assertEquals(302, reply.status);
        assertEquals(path, new URL(new URL(app.base + "/home"), reply.location).getPath());
    }

    @Test public void loginNavigationAndStaticAssetsKeepPrefix() throws Exception {
        target(app.get("/", null), "/zkui/home");
        target(app.get("/home", null), "/zkui/login");
        TestApplication.Reply page = app.get("/login", null);
        assertEquals(200, page.status);
        assertTrue(page.cookie.contains("Path=/zkui"));
        assertTrue(page.cookie.contains("HttpOnly"));
        assertTrue(page.body.contains("action=\"/zkui/login\""));
        target(app.authenticate("test-admin", null), "/zkui/home");
        String cookie = app.login("test-admin");
        String home = app.get("/home", cookie).body;
        assertTrue(home.contains("action=\"/zkui/home\""));
        assertTrue(home.contains("href=\"/zkui/home?zkPath="));
        for (String resource : new String[]{"/css/zkacd.css", "/js/table-sort.js", "/images/zookeeper.png"}) {
            assertEquals(200, app.get(resource, null).status);
        }
        HttpURLConnection outside = (HttpURLConnection) new URL(new URL(app.base), "/home").openConnection();
        outside.setConnectTimeout(1000); outside.setReadTimeout(3000); outside.setInstanceFollowRedirects(false);
        try { assertEquals(404, outside.getResponseCode()); } finally { outside.disconnect(); }
    }

    @Test public void crudImportExportRestAndHistoryWorkUnderPrefix() throws Exception {
        String cookie = app.login("test-admin");
        target(app.post("/home", "action=Save+Property&currentPath=/appconfig/&displayPath=/appconfig"
                + "&newProperty=context-key&newValue=kept", cookie), "/zkui/home");
        assertEquals("kept", app.read("/appconfig/context-key"));
        assertTrue(app.get("/export?zkPath=/appconfig", cookie).body.contains("context-key=kept"));
        target(app.upload("/appconfig=context-import=中文", cookie), "/zkui/home");
        assertEquals("中文", app.read("/appconfig/context-import"));
        assertTrue(app.get("/history", cookie).body.contains("context-key"));
        app.create("/appconfig/hosts/context-test", "/appconfig");
        assertTrue(app.get("/acd/appconfig?host=context-test&propNames=context-key", cookie).body.contains("context-key=kept"));
        assertEquals(403, app.request("/home", "action=Delete&currentPath=/appconfig/&displayPath=/appconfig"
                + "&propChkGroup=/appconfig/context-key", cookie, "application/x-www-form-urlencoded").status);
        assertEquals("kept", app.read("/appconfig/context-key"));
    }

    @Test public void logoutUsesPrefixAndStillRequiresPostAndCsrf() throws Exception {
        String cookie = app.login("test-admin");
        assertEquals(405, app.get("/logout", cookie).status);
        assertEquals(403, app.request("/logout", "", cookie, "application/x-www-form-urlencoded").status);
        target(app.post("/logout", "", cookie), "/zkui/login");
        target(app.get("/home", cookie), "/zkui/login");
    }
}
