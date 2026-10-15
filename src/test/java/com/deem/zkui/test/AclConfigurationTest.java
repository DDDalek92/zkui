package com.deem.zkui.test;

import com.deem.zkui.utils.ZkClientAuth;
import com.deem.zkui.utils.ZooKeeperUtil;
import java.util.Properties;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class AclConfigurationTest {
    @After public void resetAcl() { ZooKeeperUtil.INSTANCE.setDefaultAcl(""); }

    @Test public void invalidAclFailsWithoutEchoingItsContents() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ZooKeeperUtil.INSTANCE.setDefaultAcl("not-json-synthetic-secret"));
        assertFalse(error.toString().contains("synthetic-secret"));
        assertThrows(IllegalArgumentException.class, () -> ZooKeeperUtil.INSTANCE.setDefaultAcl("{\"acls\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> ZooKeeperUtil.INSTANCE.setDefaultAcl(
                "{\"acls\":[{\"scheme\":\"digest\",\"id\":\"user:plaintext-password\",\"perms\":\"r\"}]}"));
    }

    @Test public void clientCredentialsAreIndependentAndMustBeComplete() {
        Properties props = new Properties();
        ZkClientAuth.validate(props);
        props.setProperty("zkAuthScheme", "digest");
        assertThrows(IllegalArgumentException.class, () -> ZkClientAuth.validate(props));
        props.setProperty("zkAuthCredentials", "user:");
        assertThrows(IllegalArgumentException.class, () -> ZkClientAuth.validate(props));
        props.setProperty("zkAuthCredentials", "user:password:with:colons");
        ZkClientAuth.validate(props);
        props.setProperty("zkAuthScheme", "unsupported");
        assertThrows(IllegalArgumentException.class, () -> ZkClientAuth.validate(props));
    }
}
