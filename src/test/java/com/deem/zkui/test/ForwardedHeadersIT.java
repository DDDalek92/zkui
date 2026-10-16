package com.deem.zkui.test;

import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForwardedHeadersIT {
    @ClassRule public static TestApplication direct = new TestApplication("forwarded-disabled", "X-Forwarded-For", "false");
    @ClassRule public static TestApplication proxy = new TestApplication("forwarded-enabled", "X-Forwarded-For", "true", "trustedProxyAddresses", "127.0.0.1");
    @ClassRule public static TestApplication untrusted = new TestApplication("forwarded-untrusted", "X-Forwarded-For", "true", "trustedProxyAddresses", "192.0.2.123");

    private String forwardedCookie(TestApplication app) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(app.base + "/login").openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("X-Forwarded-Proto", "https");
        try {
            assertEquals(200, connection.getResponseCode());
            String cookie = connection.getHeaderField("Set-Cookie");
            assertNotNull(cookie);
            return cookie;
        } finally { connection.disconnect(); }
    }

    @Test public void forwardedSchemeIsTrustedOnlyWhenExplicitlyEnabled() throws Exception {
        assertFalse(forwardedCookie(direct).contains("Secure"));
        assertTrue(forwardedCookie(proxy).contains("Secure"));
        assertFalse(forwardedCookie(untrusted).contains("Secure"));
    }

    @Test public void auditAddressesIgnoreForgedHeadersExceptFromTrustedTransportPeers() throws Exception {
        for (TestApplication app : new TestApplication[]{direct, proxy, untrusted}) {
            String cookie = app.login("test-admin");
            String token = TestApplication.token(app.get("/login", cookie).body);
            HttpURLConnection connection = (HttpURLConnection) new URL(app.base + "/home").openConnection();
            connection.setConnectTimeout(1000); connection.setReadTimeout(5000); connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("X-Forwarded-For", "203.0.113.42");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            try {
                try (var out = connection.getOutputStream()) {
                    out.write(("action=Save+Property&displayPath=%2Fappconfig&newProperty=proxy-test&newValue=value&csrfToken=" + token)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                assertEquals(302, connection.getResponseCode());
            } finally { connection.disconnect(); }
            String history = app.get("/history", cookie).body;
            if (app == proxy) assertTrue(history, history.contains("203.0.113.42"));
            else { assertFalse(history, history.contains("203.0.113.42")); assertTrue(history, history.contains("127.0.0.1")); }
        }
    }

    @Test public void restImplicitHostUsesTheSameTrustedAddressBoundary() throws Exception {
        for (TestApplication app : new TestApplication[]{direct, proxy, untrusted}) {
            app.create("/appconfig/hosts/127.0.0.1", "/appconfig/direct");
            app.create("/appconfig/hosts/203.0.113.42", "/appconfig/forwarded");
            app.create("/appconfig/direct", ""); app.create("/appconfig/direct/ordinary", "transport-peer-value");
            app.create("/appconfig/forwarded", ""); app.create("/appconfig/forwarded/ordinary", "forwarded-peer-value");
            HttpURLConnection connection = (HttpURLConnection) new URL(app.base + "/acd/appconfig?propNames=ordinary").openConnection();
            connection.setConnectTimeout(1000); connection.setReadTimeout(5000); connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Cookie", app.login("test-user"));
            connection.setRequestProperty("X-Forwarded-For", "203.0.113.42");
            try {
                assertEquals(200, connection.getResponseCode());
                String body;
                try (var in = connection.getInputStream()) { body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
                assertTrue(body, body.contains(app == proxy ? "forwarded-peer-value" : "transport-peer-value"));
                assertFalse(body, body.contains(app == proxy ? "transport-peer-value" : "forwarded-peer-value"));
            } finally { connection.disconnect(); }
        }
    }
}
