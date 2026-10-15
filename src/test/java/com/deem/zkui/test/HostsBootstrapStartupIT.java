package com.deem.zkui.test;

import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class HostsBootstrapStartupIT {
    @Rule public TestApplication app = new TestApplication("hosts-bootstrap-startup",
            "initializeHosts", "true", "hostsBootstrapEntries", "{\"startup-host\":\"  app=cluster  \"}");

    @Test public void packagedStartupCreatesMappingAndRecordsOnlyCountsInHistory() throws Exception {
        assertEquals("  app=cluster  ", app.read("/appconfig/hosts/startup-host"));
        String admin = app.login("test-admin");
        TestApplication.Reply history = app.get("/history", admin);
        assertEquals(200, history.status);
        assertTrue(history.body.contains("Hosts bootstrap created=1; updated=0"));
        assertFalse(history.body.contains("app=cluster"));
        assertFalse(app.logContents().contains("app=cluster"));
    }
}
