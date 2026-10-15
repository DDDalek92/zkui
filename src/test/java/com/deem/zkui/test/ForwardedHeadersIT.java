package com.deem.zkui.test;

import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForwardedHeadersIT {
    @ClassRule public static TestApplication direct = new TestApplication("forwarded-disabled", "X-Forwarded-For", "false");
    @ClassRule public static TestApplication proxy = new TestApplication("forwarded-enabled", "X-Forwarded-For", "true");

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
    }
}
