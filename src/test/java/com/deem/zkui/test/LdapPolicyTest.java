package com.deem.zkui.test;

import com.deem.zkui.utils.LdapAuth;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.directory.DirContext;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class LdapPolicyTest {
    @Test public void preservesDomainAndUpnLoginsAndEscapesDnTemplates() throws Exception {
        Properties props = new Properties();
        props.setProperty("ldapDomain", " FIRST , SECOND ");
        assertEquals(Arrays.asList("FIRST\\alice", "SECOND\\alice"), LdapAuth.principals(props, "alice"));
        assertEquals(Arrays.asList("FIRST\\alice"), LdapAuth.principals(props, "FIRST\\alice"));
        assertEquals(Arrays.asList("alice@example.test"), LdapAuth.principals(props, "alice@example.test"));
        props.setProperty("ldapUserDnPattern", "uid={0},ou=people,dc=example,dc=test");
        assertEquals(Arrays.asList("uid=a\\,b,ou=people,dc=example,dc=test"), LdapAuth.principals(props, "a,b"));
        props.setProperty("ldapUserDnPattern", "");
        props.setProperty("userDNPattern", "cn={0},dc=example,dc=test");
        assertEquals(Arrays.asList("cn=alice,dc=example,dc=test"), LdapAuth.principals(props, "alice"));
        props.setProperty("ldapUserDnPattern", "uid=fixed,dc=example,dc=test");
        assertThrows(IllegalArgumentException.class, () -> LdapAuth.principals(props, "alice"));
    }

    @Test public void emptyPasswordsNeverReachTheDirectory() {
        AtomicInteger opens = new AtomicInteger();
        LdapAuth auth = new LdapAuth() {
            @Override protected DirContext openContext(Hashtable<String, String> env) {
                opens.incrementAndGet();
                throw new AssertionError("Empty password must not bind");
            }
        };
        Properties props = new Properties();
        props.setProperty("ldapUrl", "ldap://127.0.0.1:1");
        assertFalse(auth.authenticateUser(props, "alice", ""));
        assertFalse(auth.authenticateUser(props, "alice", null));
        assertEquals(0, opens.get());
    }

    @Test public void exactRoleAlwaysOverridesWildcard() throws Exception {
        for (String entries : new String[]{
                "{\"username\":\"alice\",\"role\":\"USER\"},{\"username\":\"*\",\"role\":\"ADMIN\"}",
                "{\"username\":\"*\",\"role\":\"ADMIN\"},{\"username\":\"alice\",\"role\":\"USER\"}"}) {
            JSONArray users = (JSONArray) new JSONParser().parse("[" + entries + "]");
            assertEquals("USER", LdapAuth.roleForUser(users, "alice"));
            assertEquals("USER", LdapAuth.roleForUser(users, "ALICE"));
            assertEquals("ADMIN", LdapAuth.roleForUser(users, "bob"));
        }
        assertEquals("USER", LdapAuth.roleForUser(new JSONArray(), "alice"));
    }

    @Test public void bindContextsAreClosedAndInvalidTimeoutsFailClosed() {
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        LdapAuth auth = new LdapAuth() {
            @Override protected DirContext openContext(Hashtable<String, String> env) throws AuthenticationException {
                opened.incrementAndGet();
                assertEquals("3000", env.get("com.sun.jndi.ldap.read.timeout"));
                assertEquals("throw", env.get(Context.REFERRAL));
                if ("BAD\\alice".equals(env.get(Context.SECURITY_PRINCIPAL))) throw new AuthenticationException("synthetic failure without reason field");
                assertEquals("GOOD\\alice", env.get(Context.SECURITY_PRINCIPAL));
                return (DirContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DirContext.class},
                        (proxy, method, args) -> { if ("close".equals(method.getName())) closed.incrementAndGet(); return null; });
            }
        };
        assertTrue(auth.authenticateUser("ldap://127.0.0.1:1", "alice", "synthetic-password", "BAD,GOOD"));
        assertEquals(2, opened.get());
        assertEquals(1, closed.get());
        Properties invalid = new Properties();
        invalid.setProperty("ldapUrl", "ldap://127.0.0.1:1");
        invalid.setProperty("ldapConnectTimeout", "0");
        assertFalse(auth.authenticateUser(invalid, "alice", "synthetic-password"));
        assertEquals(2, opened.get());
    }
}
