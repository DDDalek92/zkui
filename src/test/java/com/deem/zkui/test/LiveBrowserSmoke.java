package com.deem.zkui.test;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Actual HTTPS/Main/ZooKeeper browser flow; requires an owned, local CI fixture. */
public final class LiveBrowserSmoke {
    public static void main(String[] args) throws Throwable {
        if (!"true".equals(System.getenv("ZKUI_OWNED_BROWSER_FIXTURE"))) throw new AssertionError("Owned browser fixture required");
        TestApplication app = new TestApplication("live-browser", "baseURL", "/browser-live/", "testHttps", "true");
        boolean started = false;
        try {
            app.before(); started = true;
            ProcessBuilder builder = new ProcessBuilder("node", "src/test/js/live-browser-smoke.js").inheritIO();
            builder.environment().put("ZKUI_BROWSER_BASE", app.base);
            builder.environment().put("ZKUI_BROWSER_PASSWORD", app.browserFixturePassword());
            Process node = builder.start();
            try {
                assertTrue("Browser flow timeout", node.waitFor(60, TimeUnit.SECONDS));
                assertEquals("Live browser assertions failed", 0, node.exitValue());
            } finally {
                if (node.isAlive()) { node.destroyForcibly(); assertTrue(node.waitFor(5, TimeUnit.SECONDS)); }
            }
            for (String engine : new String[]{"chromium", "firefox"}) {
                assertEquals("browser <value> & 中文", app.read("/browser-live-" + engine));
            }
            assertNull(app.observer.exists("/browser-live-csrf-denied", false));
            assertNull(app.observer.exists("/browser-live-user-denied", false));
            System.out.println("LIVE_CHROME_FIREFOX_HTTPS_SESSION_CSRF_ROLE_REAL_ZK_MUTATION_LOGOUT_OK");
        } finally { if (started) app.after(); }
    }
}
