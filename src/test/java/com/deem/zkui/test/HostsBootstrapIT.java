package com.deem.zkui.test;

import com.deem.zkui.utils.HostsBootstrap;
import com.deem.zkui.utils.ZooKeeperUtil;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HostsBootstrapIT {
    @ClassRule public static TemporaryFolder temporary = new TemporaryFolder();
    private static IsolatedZooKeeper server;
    private ZooKeeper observer;
    private String root;
    private Properties props;

    @BeforeClass public static void start() throws Exception { server = new IsolatedZooKeeper(temporary.newFolder()); }
    @AfterClass public static void stop() { if (server != null) server.close(); }
    @Before public void fixture() throws Exception {
        observer = server.connect();
        root = "/bootstrap-fixture-" + UUID.randomUUID();
        observer.create(root, "namespace-value".getBytes(StandardCharsets.UTF_8), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        props = new Properties();
        props.setProperty("zkServer", server.address());
        props.setProperty("zkRootPath", root);
        props.setProperty("initializeHosts", "true");
        props.setProperty("hostsBootstrapEntries", "{\"host-one\":\"  中文😀\\napp=cluster  \"}");
        ZooKeeperUtil.INSTANCE.setDefaultAcl("");
    }
    @After public void cleanup() throws Exception {
        try {
            if (observer.exists(root, false) != null) ZooKeeperUtil.INSTANCE.deleteFolders(List.of(root), observer);
        } finally { observer.close(); ZooKeeperUtil.INSTANCE.setDefaultAcl(""); }
    }

    @Test public void disabledBootstrapDoesNotConnectOrCreateMetadata() throws Exception {
        props.setProperty("initializeHosts", "false");
        props.setProperty("zkServer", "intentionally-invalid-no-network-needed");
        assertFalse(HostsBootstrap.initialize(props).changed());
        assertNull(observer.exists(root + "/hosts", false));
    }

    @Test public void createsAncestorsAndIsIdempotentUnlessOverwriteIsExplicit() throws Exception {
        props.setProperty("zkHostsPath", root + "/meta/nested/hosts");
        HostsBootstrap.Result result = HostsBootstrap.initialize(props);
        assertEquals(4, result.created());
        assertEquals(0, result.updated());
        String path = root + "/meta/nested/hosts/host-one";
        Stat initial = new Stat();
        assertEquals("  中文😀\napp=cluster  ", new String(observer.getData(path, false, initial), StandardCharsets.UTF_8));
        assertFalse(HostsBootstrap.initialize(props).changed());
        props.setProperty("hostsBootstrapEntries", "{\"host-one\":\"new-mapping\"}");
        assertFalse(HostsBootstrap.initialize(props).changed());
        assertEquals(initial.getVersion(), observer.exists(path, false).getVersion());
        props.setProperty("hostsBootstrapOverwrite", "true");
        assertEquals(1, HostsBootstrap.initialize(props).updated());
        assertEquals("new-mapping", new String(observer.getData(path, false, null), StandardCharsets.UTF_8));
        assertEquals("namespace-value", new String(observer.getData(root, false, null), StandardCharsets.UTF_8));
        assertEquals(ZooDefs.Ids.OPEN_ACL_UNSAFE, observer.getACL(root, null));
        assertFalse(HostsBootstrap.initialize(props).changed());
    }

    @Test public void validatesAllEntriesAndNamespaceBeforeWriting() throws Exception {
        props.setProperty("hostsBootstrapEntries", "{\"valid-host\":\"value\",\"../escape\":\"bad\"}");
        assertThrows(IllegalArgumentException.class, () -> HostsBootstrap.initialize(props));
        assertNull(observer.exists(root + "/hosts", false));
        props.setProperty("hostsBootstrapEntries", "{}");
        props.setProperty("zkHostsPath", "/outside-fixture/hosts");
        assertThrows(com.deem.zkui.utils.PathPolicy.OutsideNamespaceException.class, () -> HostsBootstrap.initialize(props));
        assertNull(observer.exists("/outside-fixture", false));
        assertNull(observer.exists(root + "/hosts", false));
    }

    @Test public void missingRootAndBackendAclRefusalDoNotCreateOrLeakSessions() throws Exception {
        props.setProperty("zkRootPath", root + "/absent");
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> HostsBootstrap.initialize(props));
        assertNull(missing.getCause());
        assertNull(observer.exists(root + "/absent", false));
        props.setProperty("zkRootPath", root);
        observer.addAuthInfo("digest", "owner:synthetic-owner".getBytes(StandardCharsets.UTF_8));
        String digest = org.apache.zookeeper.server.auth.DigestAuthenticationProvider.generateDigest("owner:synthetic-owner");
        observer.setACL(root, new ArrayList<>(Collections.singletonList(new ACL(ZooDefs.Perms.ALL, new Id("digest", digest)))), -1);
        IllegalStateException denied = assertThrows(IllegalStateException.class, () -> HostsBootstrap.initialize(props));
        assertNull(denied.getCause());
        assertNull(observer.exists(root + "/hosts", false));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (server.sessions().size() != 1 && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals(Set.of(observer.getSessionId()), server.sessions());
    }
}
