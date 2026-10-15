package com.deem.zkui.utils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/** Explicit operator-controlled bootstrap; never deletes nodes or changes existing ACLs. */
public final class HostsBootstrap {
    private HostsBootstrap() { }
    public record Result(int created, int updated) {
        public boolean changed() { return created != 0 || updated != 0; }
    }

    private static boolean flag(Properties props, String name) {
        String value = props.getProperty(name, "false").trim();
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Invalid hosts bootstrap boolean setting");
    }

    public static Result initialize(Properties props) {
        if (!flag(props, "initializeHosts")) return new Result(0, 0);
        boolean overwrite = flag(props, "hostsBootstrapOverwrite");
        PathPolicy paths = new PathPolicy(props);
        String hosts = paths.hosts(props);
        if (!hosts.equals(paths.root())) paths.writable(hosts);
        Map<String, String> entries = parse(props, paths, hosts);
        if (hosts.substring(paths.root().length()).split("/").length > 128)
            throw new IllegalArgumentException("Hosts bootstrap path is too deep");
        try (ZooKeeper client = ServletUtil.INSTANCE.newZookeeper(props)) {
            if (client.exists(paths.root(), false) == null) throw new IllegalStateException("Configured namespace root must exist");
            int created = 0, updated = 0;
            String current = paths.root();
            for (String component : hosts.substring(paths.root().length()).split("/")) {
                if (component.isEmpty()) continue;
                String target = "/".equals(current) ? "/" + component : current + "/" + component;
                if (create(client, current, component, "")) created++;
                current = target;
            }
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String name = entry.getKey();
                if (create(client, hosts, name, entry.getValue())) created++;
                else if (overwrite) {
                    String path = hosts + "/" + name;
                    Stat stat = new Stat();
                    byte[] existing = client.getData(path, false, stat);
                    byte[] replacement = entry.getValue().getBytes(StandardCharsets.UTF_8);
                    if (!Arrays.equals(existing, replacement)) {
                        client.setData(path, replacement, stat.getVersion());
                        updated++;
                    }
                }
            }
            return new Result(created, updated);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hosts bootstrap interrupted; inspect state before retrying");
        } catch (Exception ex) {
            // Driver/network/auth errors may contain configuration; expose only this controlled message.
            throw new IllegalStateException("Hosts bootstrap failed; inspect state before retrying");
        }
    }

    private static boolean create(ZooKeeper client, String parent, String name, String value) throws Exception {
        try {
            ZooKeeperUtil.INSTANCE.createNode("/".equals(parent) ? "/" : parent + "/", name, value, client);
            return true;
        } catch (KeeperException.NodeExistsException expected) { return false; }
    }

    private static Map<String, String> parse(Properties props, PathPolicy paths, String hosts) {
        try {
            String source = props.getProperty("hostsBootstrapEntries", "{}");
            if (source.getBytes(StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException();
            Object parsed = new JSONParser().parse(source);
            if (!(parsed instanceof JSONObject) || ((JSONObject) parsed).size() > 1000) throw new IllegalArgumentException();
            Map<String, String> entries = new TreeMap<>();
            for (Object raw : ((JSONObject) parsed).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) raw;
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) throw new IllegalArgumentException();
                String key = PathPolicy.component((String) entry.getKey());
                String value = (String) entry.getValue();
                if (key.getBytes(StandardCharsets.UTF_8).length > 255 || value.getBytes(StandardCharsets.UTF_8).length > 8192)
                    throw new IllegalArgumentException();
                paths.writable(hosts + "/" + key);
                entries.put(key, value);
            }
            return entries;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid hosts bootstrap entries");
        }
    }
}
