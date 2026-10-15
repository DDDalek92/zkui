package com.deem.zkui.test;

import com.deem.zkui.utils.LdapAuth;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import java.net.InetAddress;
import java.util.Properties;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import static org.junit.Assert.*;

public class LdapAuthenticationIT {
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static InMemoryDirectoryServer directory;
    private static String url() { return "ldap://127.0.0.1:" + directory.getListenPort(); }
    private static Properties properties() {
        Properties props = new Properties();
        props.setProperty("ldapUrl", url());
        props.setProperty("ldapUserDnPattern", "uid={0},ou=people,dc=example,dc=test");
        return props;
    }
    private static TestApplication app = new TestApplication("ldap-authentication", () -> new String[]{
            "testUseLdap", "true", "ldapUrl", url(), "ldapUserDnPattern", "uid={0},ou=people,dc=example,dc=test",
            "ldapRoleSet", "{\"users\":[{\"username\":\"alice\",\"role\":\"USER\"},{\"username\":\"*\",\"role\":\"ADMIN\"}]}"});
    @ClassRule public static RuleChain resources = RuleChain.outerRule(new ExternalResource() {
        @Override protected void before() throws Exception {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=test");
            config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("test", InetAddress.getByName("127.0.0.1"), 0, null));
            config.setSchema(null);
            directory = new InMemoryDirectoryServer(config);
            directory.startListening();
            try {
                directory.add("dn: dc=example,dc=test", "objectClass: domain", "dc: example");
                directory.add("dn: ou=people,dc=example,dc=test", "objectClass: organizationalUnit", "ou: people");
                for (String user : new String[]{"alice", "bob", "a\\,b"}) {
                    directory.add("dn: uid=" + user + ",ou=people,dc=example,dc=test", "objectClass: inetOrgPerson",
                            "cn: Test User", "sn: Test", "uid: " + user.replace("\\", ""), "userPassword: " + PASSWORD);
                }
            } catch (Exception failure) { directory.shutDown(true); throw failure; }
        }
        @Override protected void after() { directory.shutDown(true); }
    }).around(app);

    @Test public void realDnBindSucceedsAndWrongOrEmptyPasswordsFail() {
        LdapAuth auth = new LdapAuth();
        assertTrue(auth.authenticateUser(properties(), "alice", PASSWORD));
        try (java.net.ServerSocket unavailable = new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = unavailable.getLocalPort();
            unavailable.close();
            Properties failover = properties();
            failover.setProperty("ldapUrl", "ldap://127.0.0.1:" + port + " " + url());
            assertTrue(auth.authenticateUser(failover, "alice", PASSWORD));
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        assertFalse(auth.authenticateUser(properties(), "alice", "wrong"));
        assertFalse(auth.authenticateUser(properties(), "alice", ""));
        assertFalse(auth.authenticateUser(properties(), null, PASSWORD));
    }

    @Test public void dnPatternEscapesUsernameInsteadOfInjectingRdns() {
        LdapAuth auth = new LdapAuth();
        assertTrue(auth.authenticateUser(properties(), "a,b", PASSWORD));
        assertFalse(auth.authenticateUser(properties(), "alice,ou=other", PASSWORD));
    }

    @Test public void httpLoginPreservesExactRoleOverLaterWildcard() throws Exception {
        TestApplication.Reply login = app.authenticateWithPassword("alice", PASSWORD, null);
        assertEquals(302, login.status);
        String cookie = login.cookie.split(";", 2)[0];
        assertEquals(200, app.get("/home", cookie).status);
        assertEquals(403, app.get("/history", cookie).status);
        TestApplication.Reply admin = app.authenticateWithPassword("bob", PASSWORD, null);
        assertEquals(302, admin.status);
        assertEquals(200, app.get("/history", admin.cookie.split(";", 2)[0]).status);
    }

    @Test public void failedReloginClearsExistingAuthentication() throws Exception {
        TestApplication.Reply login = app.authenticateWithPassword("bob", PASSWORD, null);
        String cookie = login.cookie.split(";", 2)[0];
        TestApplication.Reply failure = app.authenticateWithPassword("bob", "wrong", cookie);
        assertEquals(200, failure.status);
        assertTrue(failure.body.contains("Invalid Login"));
        assertFalse(failure.body.contains("LDAP:"));
        assertEquals(302, app.get("/home", cookie).status);
    }

    @Test(timeout = 4000) public void unavailableDirectoryIsBoundedAndFailsClosed() throws Exception {
        try (java.net.ServerSocket blackhole = new java.net.ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            Properties props = properties();
            props.setProperty("ldapUrl", "ldap://127.0.0.1:" + blackhole.getLocalPort());
            props.setProperty("ldapReadTimeout", "300");
            props.setProperty("ldapConnectTimeout", "300");
            assertFalse(new LdapAuth().authenticateUser(props, "alice", PASSWORD));
        }
    }
}
