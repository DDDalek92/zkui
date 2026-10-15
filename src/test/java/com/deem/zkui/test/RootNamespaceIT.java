package com.deem.zkui.test;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class RootNamespaceIT {
    @ClassRule public static TestApplication app = new TestApplication("root-namespace",
            "zkRootPath", "/appconfig", "zkHostsPath", "/appconfig/hosts");

    @ClassRule public static TestApplication missing = new TestApplication("root-missing",
            "zkRootPath", "/missing/inside");

    @BeforeClass public static void seed() throws Exception {
        app.create("/outside", "outside-marker");
        app.create("/outside/outside-key", "outside-marker");
        app.create("/appconfig2", "outside-prefix-marker");
        app.create("/appconfig/a", "");
        app.create("/appconfig/a/alpha", "scope-marker-a");
        app.create("/appconfig/b", "");
        app.create("/appconfig/b/beta", "scope-marker-b");
        app.create("/appconfig/delete-kept", "must-remain");
        app.create("/appconfig/hosts/good", "/appconfig/a");
        app.create("/appconfig/hosts/bad", "/outside");
        app.create("/alpha", "ancestor-secret");
        app.create("/appconfig/link&name", "");
        app.create("/appconfig/link&name/child", "link-child");
    }

    @Test public void defaultHomeAndExportStayWithinRoot() throws Exception {
        String cookie = app.login("test-admin");
        String html = app.get("/home", cookie).body;
        assertTrue(html.contains("name=\"displayPath\" value=\"/appconfig\""));
        assertFalse(html.contains("href=\"/home?zkPath=\""));
        assertFalse(html.contains("outside-marker"));
        assertTrue(html.contains("zkPath=%2Fappconfig%2Flink%26name"));
        TestApplication.Reply dump = app.get("/export", cookie);
        assertEquals(200, dump.status);
        assertTrue(dump.body.contains("scope-marker-a"));
        assertFalse(dump.body.contains("outside-marker"));
        for (String path : new String[]{"/outside", "/appconfig2"}) {
            assertEquals(403, app.get("/home?zkPath=" + path, cookie).status);
            assertEquals(403, app.get("/export?zkPath=" + path, cookie).status);
        }
        assertEquals(400, app.get("/home?zkPath=/appconfig/../outside", cookie).status);
    }

    @Test public void searchUsesTheCurrentSubtree() throws Exception {
        TestApplication.Reply reply = app.post("/home", "action=Search&searchStr=scope-marker"
                + "&displayPath=/appconfig/a&currentPath=/appconfig/a/", app.login("test-user"));
        assertEquals(200, reply.status);
        assertTrue(reply.body.contains("scope-marker-a"));
        assertFalse(reply.body.contains("scope-marker-b"));
    }

    @Test public void rejectAllDeleteTargetsBeforeApplyingAny() throws Exception {
        String cookie = app.login("test-admin");
        assertEquals(403, app.post("/home", "action=Delete&displayPath=/appconfig&currentPath=/appconfig/"
                + "&propChkGroup=/appconfig/delete-kept&propChkGroup=/outside", cookie).status);
        assertEquals("must-remain", app.read("/appconfig/delete-kept"));
        assertEquals(403, app.post("/home", "action=Save+Property&displayPath=/appconfig&currentPath=/outside/"
                + "&newProperty=injected&newValue=x", cookie).status);
        assertNull(app.observer.exists("/outside/injected", false));
        assertEquals(403, app.post("/home", "action=Delete&displayPath=/appconfig&currentPath=/appconfig/"
                + "&nodeChkGroup=/appconfig", cookie).status);
        assertNotNull(app.observer.exists("/appconfig", false));
        assertEquals(400, app.post("/home", "action=Save+Property&displayPath=/appconfig&currentPath=/appconfig/"
                + "&newProperty=../outside/injected&newValue=x", cookie).status);
    }

    @Test public void importRejectsWholeBatchWhenOneRecordEscapes() throws Exception {
        assertEquals(400, app.upload("/appconfig=not-written=x\n/outside=injected=x", app.login("test-admin")).status);
        assertNull(app.observer.exists("/appconfig/not-written", false));
        assertNull(app.observer.exists("/outside/injected", false));
    }

    @Test public void allowedOperationsWorkAndMissingRootCannotCreateOutsideAncestors() throws Exception {
        String cookie = app.login("test-admin");
        assertEquals(302, app.post("/home", "action=Save+Property&displayPath=/appconfig/a&currentPath=/appconfig/a/"
                + "&newProperty=allowed&newValue=++preserved++", cookie).status);
        assertEquals("  preserved  ", app.read("/appconfig/a/allowed"));
        assertEquals(302, app.upload("/appconfig/a=imported=中文", cookie).status);
        assertEquals("中文", app.read("/appconfig/a/imported"));
        assertEquals(400, missing.upload("/missing/inside=key=value", missing.login("test-admin")).status);
        assertNull(missing.observer.exists("/missing", false));
    }

    @Test public void restConstrainsMappingsParametersAndAncestorFallback() throws Exception {
        String cookie = app.login("test-user");
        TestApplication.Reply good = app.get("/acd/appconfig?host=good&propNames=alpha", cookie);
        assertEquals(200, good.status);
        assertTrue(good.body.contains("scope-marker-a"));
        assertFalse(good.body.contains("ancestor-secret"));
        assertEquals(403, app.get("/acd/appconfig?host=bad&propNames=outside-key", cookie).status);
        assertEquals(400, app.get("/acd/appconfig?host=good&propNames=../../outside/outside-key", cookie).status);
        assertEquals(400, app.get("/acd/appconfig?host=../outside&propNames=alpha", cookie).status);
    }
}
