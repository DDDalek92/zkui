package com.deem.zkui.test;

import com.deem.zkui.utils.ZooKeeperUtil;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeleteOverlapAccountingIT {
    @Rule public TestApplication app = new TestApplication("overlap-budget", "baseURL", "/load/");
    private static final class ReachedDescendant extends RuntimeException { }

    @Test public void overlappingSelectionsCountTheirUnionRegardlessOfOrderOrDuplicates() throws Exception {
        String root = "/load-fixture-overlap-accounting";
        app.seedLoadChildren(root, 9998, new byte[0]);
        for (List<String> selection : List.of(
                List.of(root, root + "/n000000", root + "/n000001"),
                List.of(root + "/n000001", root + "/n000000", root),
                List.of(root, root, root + "/n000000", root + "/n000001", root + "/n000001"))) {
            CountDownLatch connected = new CountDownLatch(1);
            try (ZooKeeper client = new ZooKeeper(app.zkAddress(), 10000, event -> {
                if (event.getState() == Watcher.Event.KeeperState.SyncConnected) connected.countDown();
            }) {
                private boolean rootExpanded;
                @Override public Stat exists(String path, boolean watch) throws KeeperException, InterruptedException {
                    // Intentional stop after accepting root discovery, before any delete.
                    if (rootExpanded && path.startsWith(root + "/")) throw new ReachedDescendant();
                    return super.exists(path, watch);
                }
                @Override public List<String> getChildren(String path, boolean watch) throws KeeperException, InterruptedException {
                    List<String> children = super.getChildren(path, watch);
                    if (root.equals(path)) rootExpanded = true;
                    return children;
                }
            }) {
                assertTrue(connected.await(10, TimeUnit.SECONDS));
                assertThrows("9999 unique nodes must not be counted as10001", ReachedDescendant.class,
                        () -> ZooKeeperUtil.INSTANCE.deleteFolders(selection, client));
            }
            assertEquals(9998, app.observer.exists(root, false).getNumChildren());
        }
        System.out.println("DELETE_OVERLAP_UNION_9999_BOTH_ORDERS_DUPLICATES_NO_MUTATION_OK");
    }

    @Test public void missingSelectedDescendantPreservesAncestorAndExistingChild() throws Exception {
        String root = "/load-fixture-missing";
        app.create(root, "parent");
        app.create(root + "/child", "preserved");
        for (List<String> selection : List.of(List.of(root, root + "/missing"), List.of(root + "/missing", root))) {
            KeeperException.NoNodeException error = assertThrows(KeeperException.NoNodeException.class,
                    () -> ZooKeeperUtil.INSTANCE.deleteFolders(selection, app.observer));
            assertEquals(root + "/missing", error.getPath());
            assertEquals("parent", app.read(root));
            assertEquals("preserved", app.read(root + "/child"));
        }
    }

    @Test public void similarTextualPrefixRootsAreNotCoalesced() throws Exception {
        String first = "/load-fixture-prefix", second = first + "-other";
        app.seedLoadChildren(first, 6000, new byte[0]);
        app.seedLoadChildren(second, 6000, new byte[0]);
        assertThrows(com.deem.zkui.utils.TreeWork.LimitException.class,
                () -> ZooKeeperUtil.INSTANCE.deleteFolders(List.of(first, second), app.observer));
        assertEquals(6000, app.observer.exists(first, false).getNumChildren());
        assertEquals(6000, app.observer.exists(second, false).getNumChildren());
    }
}
