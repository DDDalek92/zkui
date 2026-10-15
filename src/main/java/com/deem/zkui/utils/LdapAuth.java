/**
 *
 * Copyright (c) 2014, Deem Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.deem.zkui.utils;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class LdapAuth {
    public boolean authenticateUser(String url, String username, String password, String domains) {
        Properties props = new Properties();
        if (url != null) props.setProperty("ldapUrl", url);
        if (domains != null) props.setProperty("ldapDomain", domains);
        return authenticateUser(props, username, password);
    }

    public boolean authenticateUser(Properties props, String username, String password) {
        // Empty passwords can otherwise become an anonymous/unauthenticated LDAP bind.
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) return false;
        try {
            String url = props.getProperty("ldapUrl", "");
            String[] endpoints = url.trim().split("\\s+");
            if (endpoints.length > 8) return false;
            for (String address : endpoints) {
                java.net.URI endpoint = new java.net.URI(address);
                if ((!"ldap".equalsIgnoreCase(endpoint.getScheme()) && !"ldaps".equalsIgnoreCase(endpoint.getScheme()))
                        || endpoint.getHost() == null || endpoint.getUserInfo() != null) return false;
            }
            String connectTimeout = timeout(props, "ldapConnectTimeout");
            String readTimeout = timeout(props, "ldapReadTimeout");
            for (String principal : principals(props, username)) {
                Hashtable<String, String> env = new Hashtable<>();
                env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                env.put(Context.PROVIDER_URL, url);
                env.put(Context.SECURITY_AUTHENTICATION, "simple");
                env.put(Context.SECURITY_PRINCIPAL, principal);
                env.put(Context.SECURITY_CREDENTIALS, password);
                env.put(Context.REFERRAL, "throw");
                env.put("com.sun.jndi.ldap.connect.timeout", connectTimeout);
                env.put("com.sun.jndi.ldap.read.timeout", readTimeout);
                DirContext context = null;
                try {
                    context = openContext(env);
                    return true;
                } catch (NamingException failure) {
                    // Do not expose directory diagnostics, usernames or credentials to logs/UI.
                } finally {
                    if (context != null) {
                        try { context.close(); } catch (NamingException ignored) { }
                    }
                }
            }
        } catch (IllegalArgumentException | NamingException | java.net.URISyntaxException failure) {
            return false;
        }
        return false;
    }

    protected DirContext openContext(Hashtable<String, String> env) throws NamingException {
        return new InitialDirContext(env);
    }

    private static String timeout(Properties props, String key) {
        int value = Integer.parseInt(props.getProperty(key, "3000"));
        if (value < 1 || value > 30000) throw new IllegalArgumentException("Invalid LDAP timeout");
        return Integer.toString(value);
    }

    public static List<String> principals(Properties props, String username) throws NamingException {
        List<String> principals = new ArrayList<>();
        String pattern = props.getProperty("ldapUserDnPattern", "").trim();
        if (pattern.isEmpty()) pattern = props.getProperty("userDNPattern", "").trim();
        if (!pattern.isEmpty()) {
            if (!pattern.contains("{0}")) throw new IllegalArgumentException("DN pattern must contain {0}");
            String principal = pattern.replace("{0}", String.valueOf(Rdn.escapeValue(username)));
            new LdapName(principal);
            principals.add(principal);
        } else if (username.contains("\\") || username.contains("@") || props.getProperty("ldapDomain", "").trim().isEmpty()) {
            principals.add(username);
        } else {
            for (String domain : props.getProperty("ldapDomain").split(",")) {
                if (!domain.trim().isEmpty()) principals.add(domain.trim() + "\\" + username);
            }
        }
        if (principals.isEmpty() || principals.size() > 8) throw new IllegalArgumentException("Invalid LDAP principal configuration");
        return principals;
    }

    public static String roleForUser(JSONArray users, String username) {
        String fallback = ZooKeeperUtil.ROLE_USER;
        for (Object item : users) {
            JSONObject user = (JSONObject) item;
            String configured = ZooKeeperUtil.ROLE_ADMIN.equals(user.get("role")) ? ZooKeeperUtil.ROLE_ADMIN : ZooKeeperUtil.ROLE_USER;
            if (user.get("username") instanceof String && username.equalsIgnoreCase((String) user.get("username"))) return configured;
            if ("*".equals(user.get("username"))) fallback = configured;
        }
        return fallback;
    }
}
