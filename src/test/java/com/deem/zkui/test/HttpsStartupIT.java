package com.deem.zkui.test;

import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

/** Trusts only its own temporary certificate; hostname verification remains enabled. */
public class HttpsStartupIT {
    @ClassRule public static TestApplication app = new TestApplication("https-startup",
            "testHttps", "true", "baseURL", "/secure/");

    @Test public void ee8HttpsConnectorKeepsSecureSessionCsrfAssetsAndHistory() throws Exception {
        TestApplication.Reply login = app.get("/login", null);
        assertEquals(200, login.status);
        assertTrue(login.cookie.contains("Secure"));
        assertTrue(login.cookie.contains("HttpOnly"));
        assertTrue(login.cookie.contains("Path=/secure"));
        String cookie = app.login("test-admin");
        assertEquals(200, app.get("/home", cookie).status);
        assertEquals(200, app.get("/js/table-sort.js", null).status);
        String form = "action=Save+Property&currentPath=/appconfig/&displayPath=/appconfig"
                + "&newProperty=tls-test&newValue=owned-fixture";
        assertEquals(403, app.request("/home", form, cookie, "application/x-www-form-urlencoded").status);
        assertNull(app.observer.exists("/appconfig/tls-test", false));
        assertEquals(302, app.post("/home", form, cookie).status);
        assertEquals("owned-fixture", app.read("/appconfig/tls-test"));
        assertTrue(app.get("/history", cookie).body.contains("tls-test"));
    }
}
