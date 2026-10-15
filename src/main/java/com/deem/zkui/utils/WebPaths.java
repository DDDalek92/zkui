package com.deem.zkui.utils;

/** Validate the application's public path prefix, never a full external URL. */
public final class WebPaths {
    private WebPaths() { }

    public static String context(String configured) {
        if (configured == null || "/".equals(configured)) return "/";
        String path = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        if (path.length() > 128 || !path.matches("(/[A-Za-z0-9._~-]+)+")) {
            throw new IllegalArgumentException("Invalid baseURL configuration");
        }
        for (String segment : path.substring(1).split("/")) {
            if (".".equals(segment) || "..".equals(segment)) throw new IllegalArgumentException("Invalid baseURL configuration");
        }
        return path;
    }
}
