package com.deem.zkui.utils;

import java.util.Properties;
import org.apache.zookeeper.common.PathUtils;

/** Application namespace limits, independent of ZooKeeper's own ACLs/chroot. */
public final class PathPolicy {
    private final String root;

    public PathPolicy(Properties props) {
        root = valid(props.getProperty("zkRootPath", "/"));
        if ("/zookeeper".equals(root) || root.startsWith("/zookeeper/")) {
            throw new IllegalArgumentException("Invalid root configuration");
        }
    }

    public String root() { return root; }

    public boolean contains(String path) {
        return "/".equals(root) || root.equals(path) || path.startsWith(root + "/");
    }

    public String require(String path) {
        valid(path);
        if (!contains(path)) throw new OutsideNamespaceException();
        return path;
    }

    public String home(String path) {
        return path == null || "/".equals(path) ? root : require(path);
    }

    public String writable(String path) {
        require(path);
        if (root.equals(path) || "/zookeeper".equals(path) || path.startsWith("/zookeeper/")) {
            throw new OutsideNamespaceException();
        }
        return path;
    }

    public String parent(String path) {
        require(path);
        if (root.equals(path)) return root;
        int separator = path.lastIndexOf('/');
        return separator == 0 ? "/" : path.substring(0, separator);
    }

    public String hosts(Properties props) {
        String path = require(props.getProperty("zkHostsPath", "/".equals(root)
                ? ZooKeeperUtil.ZK_HOSTS : root + "/hosts"));
        if ("/".equals(path)) throw new IllegalArgumentException("Invalid hosts path configuration");
        return path;
    }

    public static String component(String name) {
        if (name == null || name.trim().isEmpty() || name.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Invalid node name");
        }
        valid("/" + name);
        return name;
    }

    private static String valid(String path) {
        if (path == null) throw new IllegalArgumentException("Missing node path");
        PathUtils.validatePath(path);
        return path;
    }

    public static final class OutsideNamespaceException extends SecurityException {
        public OutsideNamespaceException() { super("Path is outside the permitted namespace"); }
    }
}
