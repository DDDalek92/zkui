package com.deem.zkui.test;

import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class CsrfSecurityIT {
    @ClassRule public static TestApplication app = new TestApplication("csrf-security");
    private static final String FORM = "action=Save+Property&searchStr=&currentPath=%2Fappconfig%2F"
            + "&displayPath=%2Fappconfig&newProperty=csrf-denied&newValue=denied";

    @Test public void rejectsMissingAndInvalidTokenBeforeWriting() throws Exception {
        String cookie = app.login("test-admin");
        assertEquals(403, app.request("/home", FORM, cookie, "application/x-www-form-urlencoded").status);
        assertEquals(403, app.request("/home", FORM + "&csrfToken=invalid", cookie, "application/x-www-form-urlencoded").status);
        assertNull(app.observer.exists("/appconfig/csrf-denied", false));
    }

    @Test public void rejectsLoginWithoutPreloginToken() throws Exception {
        assertEquals(403, app.request("/login", "username=test-user&password=invalid", null,
                "application/x-www-form-urlencoded").status);
    }

    @Test public void getLogoutDoesNotEndSession() throws Exception {
        String cookie = app.login("test-user");
        assertEquals(405, app.get("/logout", cookie).status);
        assertEquals(200, app.get("/home", cookie).status);
    }

    @Test public void logoutPostRequiresToken() throws Exception {
        String cookie = app.login("test-user");
        assertEquals(403, app.request("/logout", "", cookie, "application/x-www-form-urlencoded").status);
        assertEquals(200, app.get("/home", cookie).status);
    }

    @Test public void rejectsCrossSessionTokenAndAllowsHeaderToken() throws Exception {
        String first = app.login("test-admin");
        String second = app.login("test-admin");
        String token = TestApplication.token(app.get("/home", first).body);
        assertEquals(403, app.requestWithToken("/home", FORM, second, "application/x-www-form-urlencoded", token).status);
        assertEquals(302, app.requestWithToken("/home", FORM.replace("csrf-denied", "csrf-allowed"), first,
                "application/x-www-form-urlencoded", token).status);
        assertEquals("denied", app.read("/appconfig/csrf-allowed"));
    }

    @Test public void loginRotatesCsrfToken() throws Exception {
        TestApplication.Reply page = app.get("/login", null);
        String old = TestApplication.token(page.body);
        TestApplication.Reply login = app.authenticate("test-user", page.cookie.split(";", 2)[0]);
        String cookie = login.cookie.split(";", 2)[0];
        assertNotEquals(old, TestApplication.token(app.get("/home", cookie).body));
        assertEquals(403, app.requestWithToken("/logout", "", cookie, "application/x-www-form-urlencoded", old).status);
        assertEquals(302, app.post("/logout", "", cookie).status);
        assertEquals(302, app.get("/home", cookie).status);
    }

    @Test public void adminImportWithoutTokenCannotWrite() throws Exception {
        String body = "--zkui-test\r\nContent-Disposition: form-data; name=\"file\"; filename=\"csrf.txt\"\r\n\r\n"
                + "/appconfig=csrf-import-denied=denied\r\n--zkui-test--\r\n";
        assertEquals(403, app.request("/import", body, app.login("test-admin"),
                "multipart/form-data; boundary=zkui-test").status);
        assertNull(app.observer.exists("/appconfig/csrf-import-denied", false));
    }
}
