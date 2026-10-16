package com.deem.zkui.utils;

import java.util.HashSet;
import java.util.Properties;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/** Validate local web credentials before opening a database or listening socket. */
public final class StartupSecurity {
    private StartupSecurity() { }

    public static boolean validateUsers(Properties props) {
        if ("true".equals(props.getProperty("ldapAuth"))) return false;
        boolean allowSamples = "true".equals(props.getProperty("allowSampleUsers", "false"));
        try {
            Object parsed = new JSONParser().parse(props.getProperty("userSet", ""));
            if (!(parsed instanceof JSONObject object) || !(object.get("users") instanceof JSONArray users) || users.isEmpty()) throw new IllegalArgumentException();
            HashSet<String> names = new HashSet<>();
            boolean sample = false;
            for (Object item : users) {
                if (!(item instanceof JSONObject user) || !(user.get("username") instanceof String name)
                        || !(user.get("password") instanceof String password) || !(user.get("role") instanceof String role)
                        || name.isBlank() || password.isBlank() || role.isBlank() || !names.add(name)) throw new IllegalArgumentException();
                sample |= (name.equals("admin") && password.equals("manager"))
                        || (name.equals("appconfig") && password.equals("appconfig"));
            }
            if (sample && !allowSamples) throw new IllegalArgumentException();
            return sample;
        } catch (Exception failure) {
            // No original JSON/parser exception is exposed; it may contain passwords.
            throw new IllegalArgumentException("Configure unique nonempty web credentials; sample users require explicit allowSampleUsers=true for isolated demos");
        }
    }
}
