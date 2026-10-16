package com.deem.zkui.test;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real IPv4 and IPv6 loopback transports; no parser-only or skipped coverage. */
public class Ipv6ApplicationIT {
    @ClassRule public static TestApplication ipv4 = new TestApplication("ip-stack-v4", false,
            "testEnsemble", "true", "zkHostsPath", "/hosts", "baseURL", "/ip-stack/");
    @ClassRule public static TestApplication ipv6 = new TestApplication("ip-stack-v6", true,
            "testEnsemble", "true", "zkHostsPath", "/hosts", "baseURL", "/ip-stack/");

    @BeforeClass public static void seed() throws Exception {
        for (TestApplication app : new TestApplication[]{ipv4, ipv6}) {
            app.create("/appconfig/ip-stack", "");
            app.create("/appconfig/ip-stack/value", "双栈 transport value");
            app.create("/appconfig/hosts/ip-stack-host", "/ip-stack");
        }
    }

    @Test public void actualV4AndV6ZooKeeperServersSupportFallbackChrootUiAndRest() throws Exception {
        assertTrue(ipv4.zkAddress().startsWith("127.0.0.1:"));
        assertTrue(ipv6.zkAddress().startsWith("[::1]:"));
        for (TestApplication app : new TestApplication[]{ipv4, ipv6}) {
            String cookie = app.login("test-admin");
            TestApplication.Reply page = app.get("/home?zkPath=/ip-stack/value", cookie);
            assertEquals(200, page.status);
            assertTrue(page.body.contains("双栈 transport value"));
            TestApplication.Reply rest = app.get("/acd/appconfig?host=ip-stack-host&propNames=value", cookie);
            assertEquals(200, rest.status);
            assertTrue(rest.body.contains("value=双栈 transport value"));
        }
        System.out.println("IPV4_IPV6_REAL_ZK_FALLBACK_CHROOT_HTTP_REST_OK");
    }

    @Test public void httpListenerAcceptsBothLoopbackFamiliesWithTheSameSession() throws Exception {
        String original = ipv6.base;
        String cookie = ipv6.login("test-admin");
        try {
            ipv6.base = original.replace("127.0.0.1", "[::1]");
            assertEquals(200, ipv6.get("/home?zkPath=/ip-stack", cookie).status);
            assertEquals(200, ipv6.get("/acd/appconfig?host=ip-stack-host&propNames=value", cookie).status);
        } finally { ipv6.base = original; }
        assertEquals(200, ipv6.get("/home?zkPath=/ip-stack", cookie).status);
        System.out.println("IPV4_IPV6_REAL_HTTP_LOOPBACK_SAME_SESSION_OK");
    }
}
