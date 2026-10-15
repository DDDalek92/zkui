package com.deem.zkui.utils;

import java.util.Map;
import java.util.Properties;

/** Apply the documented container overrides without editing or shell-parsing config.cfg. */
public final class EnvironmentConfig {
    private EnvironmentConfig() { }
    public static void apply(Properties props, Map<String, String> environment) {
        override(props, environment, "ZK_SERVER", "zkServer");
        override(props, environment, "USER_SET", "userSet");
        override(props, environment, "LOGIN_MESSAGE", "loginMessage");
        override(props, environment, "ZKUI_BASEURL", "baseURL");
    }
    private static void override(Properties props, Map<String, String> environment, String name, String key) {
        String value = environment.get(name);
        if (value != null) props.setProperty(key, value);
    }
}
