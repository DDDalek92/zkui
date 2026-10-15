package com.deem.zkui.test;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
public class EnvironmentOverridesIT {
    @ClassRule public static TestApplication app = new TestApplication("environment-overrides",
            "testEnvironment", "true", "baseURL", "/environment/");
    @Test public void rawEnvironmentOverridesWorkWithoutEditingOrShellEscapingConfiguration() throws Exception {
        TestApplication.Reply login = app.get("/login", null);
        assertEquals(200, login.status);
        assertTrue(login.body.contains("Environment 中文 &amp; &lt;banner&gt;"));
        assertFalse(login.body.contains("<banner>"));
        String cookie = app.login("test-admin");
        assertEquals(200, app.get("/home", cookie).status);
        assertEquals(302, app.post("/home", "action=Save+Property&currentPath=/appconfig/&displayPath=/appconfig"
                + "&newProperty=environment-key&newValue=preserved", cookie).status);
        assertEquals("preserved", app.read("/appconfig/environment-key"));
    }
}
