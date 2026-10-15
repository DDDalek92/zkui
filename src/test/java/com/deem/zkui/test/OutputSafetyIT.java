package com.deem.zkui.test;

import java.net.URLEncoder;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class OutputSafetyIT {
    @ClassRule public static TestApplication app = new TestApplication("output-safety");

    @Test public void escapesUntrustedNodeNamesAndSearchValues() throws Exception {
        app.create("/appconfig/payload-<svg onload=alert(1)>", "  first\n  <script>synthetic-markup</script>  ");
        String cookie = app.login("test-admin");
        String home = app.get("/home?zkPath=%2Fappconfig", cookie).body;
        assertFalse(home.contains("<svg onload=alert(1)>"));
        assertTrue(home.contains("&lt;svg onload=alert(1)&gt;"));
        TestApplication.Reply search = app.post("/home", "action=Search&searchStr=first", cookie);
        assertEquals(200, search.status);
        assertFalse(search.body.contains("<script>synthetic-markup</script>"));
        assertTrue(search.body.contains("&lt;script&gt;synthetic-markup&lt;/script&gt;"));
    }

    @Test public void historyAndDebugLogsDoNotStoreNewValues() throws Exception {
        String cookie = app.login("test-admin");
        String secret = "synthetic-private-token-unlabelled";
        assertEquals(302, app.post("/home", "action=Save+Property&searchStr=&currentPath=%2Fappconfig%2F"
                + "&displayPath=%2Fappconfig&newProperty=ordinary-history-key&newValue=" + URLEncoder.encode(secret, "UTF-8"), cookie).status);
        String history = app.get("/history", cookie).body;
        assertTrue(history.contains("ordinary-history-key"));
        assertFalse(history.contains(secret));
        assertFalse(app.logContents().contains(secret));
        assertEquals(403, app.get("/history", app.login("test-user")).status);
    }

    @Test public void multilineValuesHaveWhitespacePreservingMarkup() throws Exception {
        app.create("/appconfig/yaml-key", "root:\n  value: 中文  ");
        String html = app.get("/home?zkPath=%2Fappconfig", app.login("test-admin")).body;
        assertTrue(html.contains("<span class=\"mono-pre-wrap\">root:\n  value: 中文  </span>"));
        assertTrue(app.get("/css/zkacd.css", null).body.contains("white-space: pre-wrap"));
    }
}
