package com.deem.zkui.test;

import com.deem.zkui.utils.ZooKeeperUtil;
import com.deem.zkui.vo.LeafBean;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ZooKeeperOperationsIT {
    @ClassRule public static TemporaryFolder temporary = new TemporaryFolder();
    private static IsolatedZooKeeper server;
    private ZooKeeper client;
    private String root;

    @BeforeClass public static void startServer() throws Exception {
        server = new IsolatedZooKeeper(temporary.newFolder("zk"));
    }

    @AfterClass public static void stopServer() {
        if (server != null) server.close();
    }

    @Before public void connect() throws Exception {
        client = server.connect();
        root = "/test-" + UUID.randomUUID();
        client.create(root, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        ZooKeeperUtil.INSTANCE.setDefaultAcl("");
    }

    @After public void disconnect() throws Exception {
        try {
            if (client != null && root != null && client.exists(root, false) != null) {
                ZooKeeperUtil.INSTANCE.deleteFolders(Collections.singletonList(root), client);
            }
        } finally {
            if (client != null) client.close();
            ZooKeeperUtil.INSTANCE.setDefaultAcl("");
        }
    }

    @Test public void createsReadsUpdatesAndDeletesText() throws Exception {
        ZooKeeperUtil.INSTANCE.createNode(root + "/", "key", "  中文\nvalue  ", client);
        assertEquals("  中文\nvalue  ", read(root + "/key"));
        ZooKeeperUtil.INSTANCE.setPropertyValue(root + "/", "key", "updated", client);
        assertEquals("updated", read(root + "/key"));
        ZooKeeperUtil.INSTANCE.deleteLeaves(Collections.singletonList(root + "/key"), client);
        assertNull(client.exists(root + "/key", false));
    }

    @Test public void exportsAndSearchesWithReadonlyPasswordMasking() throws Exception {
        ZooKeeperUtil.INSTANCE.createNode(root + "/", "db.password", "synthetic-secret", client);
        ZooKeeperUtil.INSTANCE.createNode(root + "/", "ordinary", "find-me", client);
        Set<LeafBean> exported = ZooKeeperUtil.INSTANCE.exportTree(root, client, ZooKeeperUtil.ROLE_USER);
        assertEquals(2, exported.size());
        for (LeafBean leaf : exported) {
            if (leaf.getName().equals("db.password")) {
                assertEquals(ZooKeeperUtil.SOPA_PIPA, leaf.getStrValue());
            }
        }
        assertTrue(ZooKeeperUtil.INSTANCE.searchTree("synthetic-secret", client, ZooKeeperUtil.ROLE_USER).isEmpty());
        assertEquals(1, ZooKeeperUtil.INSTANCE.searchTree("find-me", client, ZooKeeperUtil.ROLE_USER).size());
        assertEquals(1, ZooKeeperUtil.INSTANCE.searchTree("synthetic-secret", client, ZooKeeperUtil.ROLE_ADMIN).size());
    }

    @Test public void importsMultilineTextAndHonorsOverwriteFlag() throws Exception {
        List<String> first = Arrays.asList(root + "=key=first\\nsecond");
        ZooKeeperUtil.INSTANCE.importData(first, false, client);
        assertEquals("first\nsecond", read(root + "/key"));
        List<String> replacement = Arrays.asList(root + "=key=replaced");
        ZooKeeperUtil.INSTANCE.importData(replacement, false, client);
        assertEquals("first\nsecond", read(root + "/key"));
        ZooKeeperUtil.INSTANCE.importData(replacement, true, client);
        assertEquals("replaced", read(root + "/key"));
    }

    @Test public void readonlyZooKeeperIdentityCannotWrite() throws Exception {
        client.create(root + "/readonly", "value".getBytes(StandardCharsets.UTF_8),
                ZooDefs.Ids.READ_ACL_UNSAFE, CreateMode.PERSISTENT);
        assertEquals("value", read(root + "/readonly"));
        assertThrows(KeeperException.NoAuthException.class, () ->
                ZooKeeperUtil.INSTANCE.setPropertyValue(root + "/", "readonly", "changed", client));
        assertEquals("value", read(root + "/readonly"));
        // Parent has DELETE permission; deleting the read-only leaf is allowed by ZK semantics.
    }

    @Test public void repeatedReadPermissionDoesNotBecomeWritePermission() throws Exception {
        ZooKeeperUtil.INSTANCE.setDefaultAcl("{\"acls\":[{\"scheme\":\"world\",\"id\":\"anyone\",\"perms\":\"rr\"}]}");
        ZooKeeperUtil.INSTANCE.createNode(root + "/", "read-only", "value", client);
        assertEquals("value", read(root + "/read-only"));
        assertThrows(KeeperException.NoAuthException.class,
                () -> client.setData(root + "/read-only", "denied".getBytes(StandardCharsets.UTF_8), -1));
    }

    private String read(String path) throws Exception {
        return new String(client.getData(path, false, null), StandardCharsets.UTF_8);
    }
}
