package com.deem.zkui.test;

import java.sql.Timestamp;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original V1 permits NULL in every non-ID history column; old records must remain displayable. */
public class NullableHistoryIT {
    @Rule public TestApplication app = new TestApplication("nullable-history", "baseURL", "/legacy/");
    private static final long HIGH_ID = 9007199254740993L;

    private void seed() throws Exception {
        app.seedHistoryRow(41L, null, null, null, null);
        app.seedHistoryRow(HIGH_ID, "<img src=x onerror=alert(1)>", new Timestamp(1700000000123L),
                "  <script>synthetic-history</script> 中文😀  ", "<svg onload=alert(2)>");
        app.seedHistoryRow(HIGH_ID + 10, "", null, "", "");
    }

    @Test public void nullableOriginalRowsRenderWithoutErrorsOrUnescapedFields() throws Exception {
        seed();
        String admin = app.login("test-admin");
        TestApplication.Reply page = app.get("/history", admin);
        assertEquals(200, page.status);
        assertTrue(page.body.contains("<td>41</td>"));
        assertTrue("SQL NULL must display as empty cells", java.util.regex.Pattern.compile(
                "<td>41</td>(?:\\s*<td></td>){4}").matcher(page.body).find());
        assertTrue("Large IDs must be rendered exactly", page.body.contains("<td>" + HIGH_ID + "</td>"));
        assertTrue(page.body.contains("<td>" + (HIGH_ID + 10) + "</td>"));
        assertTrue(page.body.contains("&lt;script&gt;synthetic-history&lt;/script&gt; 中文😀"));
        assertTrue(page.body.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(page.body.contains("&lt;svg onload=alert(2)&gt;"));
        assertFalse(page.body.contains("<script>synthetic-history</script>"));
        assertFalse(page.body.contains("<img src=x onerror=alert(1)>"));
        assertFalse(page.body.contains("<svg onload=alert(2)>"));
        assertFalse(page.body.contains("FreeMarker template error"));
        assertTrue(page.body.contains("action=\"/legacy/history\""));
        // Re-read through HTTP: rendering must not mutate or consume the old records.
        TestApplication.Reply again = app.get("/history", admin);
        assertEquals(200, again.status);
        assertTrue(again.body.contains("<td>41</td>"));
        assertTrue(again.body.contains("<td>" + HIGH_ID + "</td>"));
        assertTrue(again.body.contains("&lt;script&gt;synthetic-history&lt;/script&gt; 中文😀"));
        assertFalse(app.logContents().contains("InvalidReferenceException"));
    }

    @Test public void legacyNullableRowsDoNotBypassHistoryAdministratorRestriction() throws Exception {
        seed();
        String user = app.login("test-user");
        TestApplication.Reply reply = app.get("/history", user);
        assertEquals(403, reply.status);
        assertFalse(reply.body.contains("synthetic-history"));
        assertFalse(reply.body.contains(Long.toString(HIGH_ID)));
    }
}
