package com.deem.zkui.test;

import com.deem.zkui.utils.ZooKeeperUtil;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.*;
import org.apache.zookeeper.ZooKeeper;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ConnectionReliabilityIT {
    @ClassRule public static TestApplication app = new TestApplication("connection-reuse");
    @ClassRule public static TestApplication ensemble = new TestApplication("connection-ensemble", "testEnsemble", "true");
    @ClassRule public static TestApplication unavailable = new TestApplication("connection-unavailable",
            "testNoZooKeeper", "true", "zkConnectTimeout", "1");
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 10000) public void waitsForDelayedServerInsteadOfReturningUnconnectedClient() throws Exception {
        int port;
        try (ServerSocket slot = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) { port = slot.getLocalPort(); }
        final int serverPort = port;
        File work = temporary.newFolder();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<IsolatedZooKeeper> delayed = worker.submit(() -> {
            Thread.sleep(400);
            return new IsolatedZooKeeper(work, serverPort);
        });
        try {
            try (ZooKeeper client = ZooKeeperUtil.INSTANCE.createZKConnection("127.0.0.1:" + port, 5000)) {
                assertEquals(ZooKeeper.States.CONNECTED, client.getState());
            }
        } finally {
            delayed.get(5, TimeUnit.SECONDS).close();
            worker.shutdownNow();
        }
    }

    @Test(timeout = 5000) public void unreachableServerFailsWithinBound() throws Exception {
        try (ServerSocket blackhole = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            long start = System.nanoTime();
            try (ZooKeeper client = ZooKeeperUtil.INSTANCE.createZKConnection("127.0.0.1:" + blackhole.getLocalPort(), 600)) {
                fail("Must fail rather than return an unconnected client");
            } catch (IOException expected) {
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
            }
        }
    }

    @Test public void restDoesNotCloseTheUiConnection() throws Exception {
        app.create("/appconfig/hosts/reuse.example", "/appconfig");
        app.create("/appconfig/reuse-key", "visible");
        String cookie = app.login("test-user");
        assertEquals(200, app.get("/home", cookie).status);
        Set<Long> original = app.sessions();
        assertEquals(2, original.size());
        assertEquals(200, app.get("/acd/appconfig?host=reuse.example&propNames=reuse-key", cookie).status);
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < end && !app.sessions().equals(original)) Thread.sleep(20);
        assertEquals(original, app.sessions());
        assertEquals(200, app.get("/home", cookie).status);
        assertEquals(original, app.sessions());
    }

    @Test public void unavailableConnectionReturns503() throws Exception {
        assertEquals(503, unavailable.get("/home", unavailable.login("test-user")).status);
    }

    @Test public void ensembleWithUnavailableFirstServerAndChrootWorks() throws Exception {
        String cookie = ensemble.login("test-user");
        TestApplication.Reply home = ensemble.get("/home", cookie);
        assertEquals(200, home.status);
        assertTrue(home.body.contains("hosts"));
        assertEquals(200, ensemble.get("/export?zkPath=%2F", cookie).status);
        TestApplication.Reply monitor = ensemble.get("/monitor", cookie);
        assertEquals(200, monitor.status);
        assertFalse(monitor.body.contains("NumberFormatException"));
        assertTrue(monitor.body.contains("Server:"));
    }
}
