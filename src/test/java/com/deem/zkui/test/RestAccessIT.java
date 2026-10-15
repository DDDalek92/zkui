package com.deem.zkui.test;

import com.deem.zkui.utils.ZooKeeperUtil;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class RestAccessIT {
    @ClassRule public static TestApplication defaults = new TestApplication("rest-defaults");
    @ClassRule public static TestApplication legacy = new TestApplication("rest-legacy", "blockPwdOverRest", "false");
    @ClassRule public static TestApplication missing = new TestApplication("rest-missing",
            "allowAnonymousRest", null, "blockPwdOverRest", null);
    @ClassRule public static TestApplication invalid = new TestApplication("rest-invalid",
            "allowAnonymousRest", "invalid", "blockPwdOverRest", "invalid");
    @ClassRule public static TestApplication anonymous = new TestApplication("rest-anonymous",
            "allowAnonymousRest", "true", "blockPwdOverRest", "false");

    private static final String LOOKUP = "/acd/appconfig?host=test.example&propNames=ordinary"
            + "&propNames=db.password&propNames=db.Password&propNames=db.PaSsWd";

    @BeforeClass public static void seed() throws Exception {
        for (TestApplication app : new TestApplication[]{defaults, legacy, missing, invalid, anonymous}) {
            app.create("/appconfig/hosts/test.example", "/appconfig/demo");
            app.create("/appconfig/demo", "");
            app.create("/appconfig/demo/ordinary", "visible-value");
            app.create("/appconfig/demo/db.password", "synthetic-password");
            app.create("/appconfig/demo/db.Password", "synthetic-mixed-password");
            app.create("/appconfig/demo/db.PaSsWd", "synthetic-mixed-passwd");
        }
    }

    @Test public void anonymousDefaultIsUnauthorizedWithoutSessionCreation() throws Exception {
        TestApplication.Reply reply = defaults.get(LOOKUP, null);
        assertFalse("Anonymous REST must not expose a password", reply.body.contains("synthetic-"));
        assertEquals(401, reply.status);
        assertNull("Rejected REST requests must not allocate sessions", reply.cookie);
    }

    @Test public void legacyFalseDoesNotDisableAuthentication() throws Exception {
        assertEquals(401, legacy.get(LOOKUP, null).status);
    }

    @Test public void defaultsMaskPasswordsForBothUserAndAdmin() throws Exception {
        assertMasked(defaults.get(LOOKUP, defaults.login("test-user")));
        assertMasked(defaults.get(LOOKUP, defaults.login("test-admin")));
    }

    @Test public void legacyFalseDoesNotElevateReadonlyOrUnknownRole() throws Exception {
        assertMasked(legacy.get(LOOKUP, legacy.login("test-user")));
        assertMasked(legacy.get(LOOKUP, legacy.login("test-other")));
    }

    @Test public void explicitFalseAllowsOnlyAuthenticatedAdminToReadPasswords() throws Exception {
        TestApplication.Reply reply = legacy.get(LOOKUP, legacy.login("test-admin"));
        assertEquals(200, reply.status);
        assertEquals("no-store", reply.cacheControl);
        assertTrue(reply.body.contains("db.password=synthetic-password"));
        assertTrue(reply.body.contains("db.Password=synthetic-mixed-password"));
    }

    @Test public void missingSettingsFailClosed() throws Exception {
        assertEquals(401, missing.get(LOOKUP, null).status);
        assertMasked(missing.get(LOOKUP, missing.login("test-admin")));
    }

    @Test public void invalidSettingsFailClosed() throws Exception {
        assertEquals(401, invalid.get(LOOKUP, null).status);
        assertMasked(invalid.get(LOOKUP, invalid.login("test-admin")));
    }

    @Test public void anonymousCompatibilityModeNeverElevatesAnonymousOrUser() throws Exception {
        assertMasked(anonymous.get(LOOKUP, null));
        assertMasked(anonymous.get(LOOKUP, anonymous.login("test-user")));
        assertEquals(302, anonymous.get("/home", null).status);
    }

    @Test public void malformedLookupHasControlledStatus() throws Exception {
        String cookie = defaults.login("test-user");
        assertEquals(400, defaults.get("/acd/appconfig?host=test.example", cookie).status);
        assertEquals(400, defaults.get("/acd/appconfig?host=test.example&propNames=", cookie).status);
        assertEquals(404, defaults.get("/acd/appconfig?host=unknown.example&propNames=ordinary", cookie).status);
    }

    @Test public void allowlistedNamesInsideOtherPathsDoNotBypassLogin() throws Exception {
        assertEquals(302, defaults.get("/login/extra", null).status);
        assertEquals(302, defaults.get("/acd/appconfig/extra", null).status);
        assertEquals(302, anonymous.get("/acd/appconfig/extra", null).status);
        assertEquals(200, defaults.get("/login", null).status);
    }

    private void assertMasked(TestApplication.Reply reply) {
        assertEquals(200, reply.status);
        assertEquals("no-store", reply.cacheControl);
        assertTrue(reply.body.contains("ordinary=visible-value"));
        assertTrue(reply.body.contains("db.password=" + ZooKeeperUtil.SOPA_PIPA));
        assertTrue(reply.body.contains("db.Password=" + ZooKeeperUtil.SOPA_PIPA));
        assertTrue(reply.body.contains("db.PaSsWd=" + ZooKeeperUtil.SOPA_PIPA));
        assertFalse("Masked responses must not contain any synthetic password", reply.body.contains("synthetic-"));
    }
}
