package com.deem.zkui.test;

import java.util.Locale;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionSecurityIT {
    @ClassRule public static TestApplication app = new TestApplication("session-security");

    @Test public void loginRotatesSessionAndInvalidatesPreviousId() throws Exception {
        String before = app.get("/login", null).cookie.split(";", 2)[0];
        TestApplication.Reply login = app.authenticate("test-admin", before);
        assertEquals(302, login.status);
        assertNotNull("Login must issue a new session cookie", login.cookie);
        String after = login.cookie.split(";", 2)[0];
        assertNotEquals(before, after);
        assertEquals(302, app.get("/home", before).status);
        assertEquals(200, app.get("/home", after).status);
    }

    @Test public void sessionCookieIsHttpOnlyAndCannotAuthenticateByUrl() throws Exception {
        TestApplication.Reply login = app.authenticate("test-user", null);
        assertTrue(login.cookie.toLowerCase(Locale.ROOT).contains("httponly"));
        String id = login.cookie.split(";", 2)[0].split("=", 2)[1];
        assertEquals(302, app.get("/home;jsessionid=" + id, null).status);
    }

    @Test public void logoutInvalidatesAnUnusedSession() throws Exception {
        String cookie = app.login("test-user");
        assertEquals(302, app.get("/logout", cookie).status);
        assertEquals(302, app.get("/home", cookie).status);
    }
}
