package com.deem.zkui.test;

import com.deem.zkui.utils.ZkClientAuth;
import com.deem.zkui.utils.ZooKeeperUtil;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Only the loopback-owned CI legacy server; no arbitrary endpoint or production path. */
public final class ZkCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("ZKUI_OWNED_ZK_FIXTURE"))) throw new AssertionError("Owned CI server required");
        String root = "/zkui-ci-" + UUID.randomUUID();
        ZooKeeper client = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline && client == null) {
            try { client = ZooKeeperUtil.INSTANCE.createZKConnection("127.0.0.1:1,127.0.0.1:12181", 10000, 3000); }
            catch (java.io.IOException expected) { Thread.sleep(100); }
        }
        if (client == null) throw new AssertionError("Legacy fixture did not become ready");
        Properties auth = new Properties();
        auth.setProperty("zkAuthScheme", "digest"); auth.setProperty("zkAuthCredentials", "ci-user:synthetic-ci-only");
        try {
            ZkClientAuth.apply(client, auth);
            client.create(root, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            String key = root + "/private";
            List<ACL> acl = List.of(new ACL(ZooDefs.Perms.ALL, new Id("auth", "")));
            client.create(key, "  中文😀\nvalue  ".getBytes(StandardCharsets.UTF_8), acl, CreateMode.PERSISTENT);
            Stat stat = new Stat();
            if (!"  中文😀\nvalue  ".equals(new String(client.getData(key, false, stat), StandardCharsets.UTF_8))) throw new AssertionError("Text changed");
            if (!client.getChildren(root, false).equals(List.of("private"))) throw new AssertionError("Child listing changed");
            client.setData(key, "updated".getBytes(StandardCharsets.UTF_8), stat.getVersion());
            try (ZooKeeper anonymous = ZooKeeperUtil.INSTANCE.createZKConnection("127.0.0.1:12181", 10000, 3000)) {
                try { anonymous.getData(key, false, null); throw new AssertionError("Anonymous identity read protected data"); }
                catch (KeeperException.NoAuthException expected) { }
            }
            try (ZooKeeper reconnected = ZooKeeperUtil.INSTANCE.createZKConnection("127.0.0.1:12181", 10000, 3000)) {
                ZkClientAuth.apply(reconnected, auth);
                if (!"updated".equals(new String(reconnected.getData(key, false, null), StandardCharsets.UTF_8))) throw new AssertionError("Reconnect lost authenticated data");
                reconnected.delete(key, -1);
            }
            client.delete(root, -1);
            System.out.println("ZK396_CLIENT_TO_357_SERVER_ENSEMBLE_DIGEST_ACL_TEXT_RECONNECT_CRUD_OK");
        } finally {
            // Only this generated root on the owned CI server.
            try {
                if (client.exists(root + "/private", false) != null) client.delete(root + "/private", -1);
                if (client.exists(root, false) != null) client.delete(root, -1);
            } finally { client.close(); }
        }
    }
}
