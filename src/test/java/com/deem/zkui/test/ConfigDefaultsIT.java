package com.deem.zkui.test;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigDefaultsIT {
    @ClassRule public static TestApplication app = new TestApplication("config-defaults",
            "https", null, "X-Forwarded-For", null, "historyLimit", "2");

    @Test public void optionalFlagsCanBeAbsentAndHistoryHeadingMatchesTheQueryLimit() throws Exception {
        String cookie = app.login("test-admin");
        assertEquals(200, app.get("/home", cookie).status);
        TestApplication.Reply history = app.get("/history", cookie);
        assertEquals(200, history.status);
        assertTrue(history.body.contains("Top 2 Changes"));
        assertEquals(302, app.post("/history", "", cookie).status);
    }
}
