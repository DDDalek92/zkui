package com.deem.zkui.test;

import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class LargeTreeIT {
    @Rule public TestApplication app = new TestApplication("large-tree", "baseURL", "/load/");

    @Test public void pages312763RealServerChildrenAndBoundsFullTreeOperations() throws Exception {
        String root = "/load-fixture-wide";
        app.seedLoadChildren(root, 312763, "synthetic-load-value".getBytes(StandardCharsets.UTF_8));
        String admin = app.login("test-admin");
        TestApplication.Reply first = app.get("/home?zkPath=" + root, admin);
        assertEquals(200, first.status);
        assertTrue(first.body.contains("Page 1 of 3128; 312763 children"));
        assertTrue(first.body.contains("n000000"));
        assertTrue(first.body.contains("n000099"));
        assertFalse(first.body.contains("n000100"));
        assertTrue(first.body.contains("/load/home?zkPath="));
        TestApplication.Reply second = app.get("/home?zkPath=" + root + "&page=2", admin);
        assertEquals(200, second.status);
        assertTrue(second.body.contains("n000100"));
        assertFalse(second.body.contains("n000099"));
        TestApplication.Reply last = app.get("/home?zkPath=" + root + "&page=3128", admin);
        assertEquals(200, last.status);
        assertTrue(last.body.contains("Page 3128 of 3128"));
        assertTrue(last.body.contains("n312762"));
        assertEquals(400, app.get("/home?zkPath=" + root + "&page=3129", admin).status);
        assertEquals(400, app.get("/home?zkPath=" + root + "&page=0", admin).status);
        assertEquals(400, app.get("/home?zkPath=" + root + "&page=oops", admin).status);
        assertEquals(413, app.get("/export?zkPath=" + root, admin).status);
        assertEquals(413, app.post("/home", "action=Search&displayPath=" + root + "&searchStr=synthetic", admin).status);
        assertEquals(413, app.post("/home", "action=Delete&displayPath=%2F&currentPath=%2F&nodeChkGroup=" + root, admin).status);
        assertEquals(312763, app.observer.exists(root, false).getNumChildren());
        assertEquals("synthetic-load-value", app.read(root + "/n312762"));
    }

    @Test public void combinedDeleteSelectionIsPreflightedBeforeAnyRootIsRemoved() throws Exception {
        String first = "/load-fixture-first", second = "/load-fixture-second";
        app.seedLoadChildren(first, 6000, new byte[0]);
        app.seedLoadChildren(second, 6000, new byte[0]);
        String admin = app.login("test-admin");
        assertEquals(413, app.post("/home", "action=Delete&displayPath=%2F&currentPath=%2F"
                + "&nodeChkGroup=" + first + "&nodeChkGroup=" + second, admin).status);
        assertEquals(6000, app.observer.exists(first, false).getNumChildren());
        assertEquals(6000, app.observer.exists(second, false).getNumChildren());
    }

    @Test public void overlappingDeleteSelectionsRemoveEachNodeOnce() throws Exception {
        String root = "/load-fixture-overlap";
        app.create(root, "");
        app.create(root + "/child", "");
        app.create(root + "/child/leaf", "value");
        String admin = app.login("test-admin");
        assertEquals(302, app.post("/home", "action=Delete&displayPath=%2F&currentPath=%2F"
                + "&nodeChkGroup=" + root + "/child&nodeChkGroup=" + root, admin).status);
        assertNull(app.observer.exists(root, false));
    }

    @Test public void pageValueBudgetPreventsUnboundedRenderedResponses() throws Exception {
        String root = "/load-fixture-bytes";
        app.seedLoadChildren(root, 26, new byte[400000]);
        String admin = app.login("test-admin");
        assertEquals(413, app.get("/home?zkPath=" + root, admin).status);
        assertEquals(26, app.observer.exists(root, false).getNumChildren());
    }

    @Test public void deepTreeExportAndDeleteStopBeforeMutating() throws Exception {
        String root = "/load-fixture-deep";
        app.create(root, "");
        String path = root;
        for (int i = 0; i < 130; i++) {
            path += "/d";
            app.create(path, "");
        }
        app.create(path + "/leaf", "preserved");
        String admin = app.login("test-admin");
        assertEquals(413, app.get("/export?zkPath=" + root, admin).status);
        assertEquals(413, app.post("/home", "action=Delete&displayPath=%2F&currentPath=%2F&nodeChkGroup=" + root, admin).status);
        assertEquals("preserved", app.read(path + "/leaf"));
    }
}
