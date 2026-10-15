package com.deem.zkui.test;

import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuditFailureIT {
    @Rule public TestApplication app = new TestApplication("audit-failure");

    @Test public void nodeMutationReportsFailedAuditAndDoesNotClaimSuccess() throws Exception {
        String admin = app.login("test-admin");
        app.dropHistoryTable();
        TestApplication.Reply reply = app.post("/home",
                "action=Save+Property&currentPath=%2F&displayPath=%2F&newProperty=audit-fixture&newValue=synthetic-value", admin);
        assertEquals(503, reply.status);
        assertTrue(reply.body.contains("inspect state before retrying"));
        assertEquals("synthetic-value", app.read("/audit-fixture"));
        assertFalse(app.get("/home", admin).body.contains("Property Saved!"));
        assertFalse(app.logContents().contains("synthetic-value"));
    }

    @Test public void importReportsFailedAuditAndDoesNotClaimSuccess() throws Exception {
        String admin = app.login("test-admin");
        app.dropHistoryTable();
        TestApplication.Reply reply = app.upload("/=import-audit-fixture=synthetic-import-value", admin);
        assertEquals(503, reply.status);
        assertTrue(reply.body.contains("inspect state before retrying"));
        assertEquals("synthetic-import-value", app.read("/import-audit-fixture"));
        assertFalse(app.get("/home", admin).body.contains("Import Completed!"));
        assertFalse(app.logContents().contains("synthetic-import-value"));
    }
}
