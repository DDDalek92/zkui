package com.deem.zkui.utils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.apache.zookeeper.ZooKeeper;

/** Client identity is separate from the ACL assigned to newly created nodes. */
public final class ZkClientAuth {
    private ZkClientAuth() { }

    public static void validate(Properties props) {
        String scheme = props.getProperty("zkAuthScheme", "").trim();
        String credentials = props.getProperty("zkAuthCredentials", "");
        if (scheme.isEmpty() && credentials.isEmpty()) return;
        int separator = credentials.indexOf(':');
        if (!"digest".equals(scheme) || separator < 1 || separator == credentials.length() - 1) {
            throw new IllegalArgumentException("Configure zkAuthScheme=digest and a nonempty username:password credential");
        }
    }

    public static void apply(ZooKeeper client, Properties props) {
        validate(props);
        String scheme = props.getProperty("zkAuthScheme", "").trim();
        if (!scheme.isEmpty()) client.addAuthInfo(scheme, props.getProperty("zkAuthCredentials").getBytes(StandardCharsets.UTF_8));
    }
}
