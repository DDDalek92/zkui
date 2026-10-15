package com.deem.zkui.test;

import com.deem.zkui.utils.ZooKeeperUtil;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.auth.DigestAuthenticationProvider;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class DigestAuthenticationIT {
    private static final String WRITER = "synthetic-writer:" + UUID.randomUUID();
    private static final String READER = "synthetic-reader:" + UUID.randomUUID();
    @ClassRule public static TestApplication correct = new TestApplication("digest-correct",
            "zkAuthScheme", "digest", "zkAuthCredentials", WRITER,
            "defaultAcl", "{\"acls\":[{\"scheme\":\"auth\",\"id\":\"\",\"perms\":\"*\"}]}");
    @ClassRule public static TestApplication wrong = new TestApplication("digest-wrong",
            "zkAuthScheme", "digest", "zkAuthCredentials", "synthetic-writer:wrong");
    @ClassRule public static TestApplication readonly = new TestApplication("digest-readonly",
            "zkAuthScheme", "digest", "zkAuthCredentials", READER);

    @BeforeClass public static void seedProtectedNodes() throws Exception {
        for (TestApplication app : new TestApplication[]{correct, wrong, readonly}) {
            app.observer.addAuthInfo("digest", WRITER.getBytes(StandardCharsets.UTF_8));
            java.util.List<ACL> acl = Arrays.asList(
                    new ACL(ZooDefs.Perms.ALL, new Id("digest", DigestAuthenticationProvider.generateDigest(WRITER))),
                    new ACL(ZooDefs.Perms.READ, new Id("digest", DigestAuthenticationProvider.generateDigest(READER))));
            app.observer.create("/appconfig/private", new byte[0], acl, CreateMode.PERSISTENT);
            app.observer.create("/appconfig/private/ordinary", "visible".getBytes(StandardCharsets.UTF_8), acl, CreateMode.PERSISTENT);
            app.observer.create("/appconfig/hosts/digest.example", "/appconfig/private".getBytes(StandardCharsets.UTF_8), acl, CreateMode.PERSISTENT);
        }
    }

    @Test public void configuredDigestWorksForUiExportAndRest() throws Exception {
        String cookie = correct.login("test-user");
        assertTrue(correct.get("/home?zkPath=%2Fappconfig%2Fprivate", cookie).body.contains("visible"));
        assertTrue(correct.get("/export?zkPath=%2Fappconfig%2Fprivate", cookie).body.contains("ordinary=visible"));
        assertTrue(correct.get("/acd/appconfig?host=digest.example&propNames=ordinary", cookie).body.contains("ordinary=visible"));
    }

    @Test public void incorrectIdentityIsDeniedOnAllReadPaths() throws Exception {
        String cookie = wrong.login("test-admin");
        assertEquals(403, wrong.get("/home?zkPath=%2Fappconfig%2Fprivate", cookie).status);
        assertEquals(403, wrong.get("/export?zkPath=%2Fappconfig%2Fprivate", cookie).status);
        assertEquals(403, wrong.get("/acd/appconfig?host=digest.example&propNames=ordinary", cookie).status);
    }

    @Test public void webAdminCannotBypassReadonlyZooKeeperIdentity() throws Exception {
        String cookie = readonly.login("test-admin");
        assertTrue(readonly.get("/home?zkPath=%2Fappconfig%2Fprivate", cookie).body.contains("visible"));
        assertEquals(403, readonly.post("/home", "action=Save+Property&searchStr=&currentPath=%2Fappconfig%2Fprivate%2F"
                + "&displayPath=%2Fappconfig%2Fprivate&newProperty=denied&newValue=denied", cookie).status);
        assertNull(readonly.observer.exists("/appconfig/private/denied", false));
    }

    @Test public void createdNodeUsesCreatorAclAndAnonymousClientCannotReadIt() throws Exception {
        String cookie = correct.login("test-admin");
        assertEquals(302, correct.post("/home", "action=Save+Property&searchStr=&currentPath=%2Fappconfig%2Fprivate%2F"
                + "&displayPath=%2Fappconfig%2Fprivate&newProperty=protected-new&newValue=value", cookie).status);
        assertEquals("value", correct.read("/appconfig/private/protected-new"));
        try (ZooKeeper anonymous = ZooKeeperUtil.INSTANCE.createZKConnection(correct.zkAddress(), 5000)) {
            assertThrows(KeeperException.NoAuthException.class, () -> anonymous.getData("/appconfig/private/protected-new", false, null));
        }
        assertFalse(correct.logContents().contains(WRITER));
    }
}
