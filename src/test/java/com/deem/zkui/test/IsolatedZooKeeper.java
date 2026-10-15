package com.deem.zkui.test;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;

/** Test-owned server: loopback, OS-assigned port, and caller-owned temporary files. */
final class IsolatedZooKeeper implements AutoCloseable {
    private final ZooKeeperServer server;
    private final NIOServerCnxnFactory factory;

    IsolatedZooKeeper(File directory) throws Exception {
        this(directory, 0);
    }

    IsolatedZooKeeper(File directory, int port) throws Exception {
        server = new ZooKeeperServer(directory, directory, 2000);
        factory = new NIOServerCnxnFactory();
        try {
            factory.configure(new InetSocketAddress("127.0.0.1", port), 20);
            factory.startup(server);
        } catch (Exception e) {
            close();
            throw e;
        }
    }

    String address() {
        return "127.0.0.1:" + factory.getLocalPort();
    }

    ZooKeeper connect() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        ZooKeeper client = new ZooKeeper(address(), 10000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        if (!connected.await(10, TimeUnit.SECONDS)) {
            client.close();
            throw new IllegalStateException("Isolated ZooKeeper connection timed out");
        }
        return client;
    }

    java.util.Set<Long> sessions() {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (org.apache.zookeeper.server.ServerCnxn connection : factory.getConnections()) {
            if (connection.getSessionId() != 0) ids.add(connection.getSessionId());
        }
        return ids;
    }

    @Override
    public void close() {
        factory.shutdown();
        server.shutdown();
    }
}
