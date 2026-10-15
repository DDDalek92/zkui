package com.deem.zkui.dao;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Locale;

/** Refuse a parallel empty database when a local H2 base has the other file format. */
final class H2FileFormatGuard {
    private H2FileFormatGuard() { }

    static void check(String driverClass, String url) throws SQLException {
        boolean h2Class = "org.h2.Driver".equals(driverClass);
        boolean h2Url = url != null && url.startsWith("jdbc:h2:");
        if (!h2Class && !h2Url) return; // MySQL and other configured backends are unchanged.
        if (!h2Class || !h2Url) throw refused();
        try {
            String base = localBase(url);
            if (base == null) return; // Memory/server/archive URLs have no inspectable local base.
            Driver driver = (Driver) Class.forName(driverClass).getDeclaredConstructor().newInstance();
            boolean pageStore = driver.getMajorVersion() == 1 && driver.getMinorVersion() == 3;
            boolean modern = driver.getMajorVersion() == 2;
            if (!pageStore && !modern) throw refused(); // No unvalidated H2 1.4 format assumptions.
            if (pageStore && Boolean.getBoolean("h2.mvStore")) throw refused();
            Path path = Path.of(base).toAbsolutePath();
            String incompatible = pageStore ? ".mv.db" : ".h2.db";
            // Check both OS traversal and H2's canonical-file interpretation, without lexically
            // erasing symlink/.. components before inspecting the filesystem.
            Path canonical = path.toFile().getCanonicalFile().toPath();
            if (exists(Path.of(path + incompatible)) || exists(Path.of(canonical + incompatible))) throw refused();
        } catch (ReflectiveOperationException | IOException | InvalidPathException | SecurityException ex) {
            // Never include URL settings, filesystem paths or driver exception messages.
            throw refused();
        }
    }

    private static String localBase(String url) throws SQLException {
        String base = url.substring("jdbc:h2:".length());
        int settings = base.indexOf(';');
        if (settings >= 0) {
            if (settings > 0 && base.charAt(settings - 1) == '\\') throw refused();
            for (String setting : base.substring(settings + 1).split(";")) {
                String key = setting.split("=", 2)[0].trim();
                if ("MV_STORE".equalsIgnoreCase(key)) throw refused();
            }
            base = base.substring(0, settings);
        }
        if (base.startsWith("mem:") || base.startsWith("tcp:")
                || base.startsWith("ssl:") || base.startsWith("zip:")) return null;
        if (base.startsWith("file:")) base = base.substring("file:".length());
        String folded = base.toLowerCase(Locale.ROOT);
        if (base.isEmpty() || folded.endsWith(".mv.db") || folded.endsWith(".h2.db")) throw refused();
        // H2 also has virtual filesystems (nio, split, etc.). Do not pretend their strings are local paths.
        if (base.indexOf(':') >= 0 && !base.matches("[A-Za-z]:[\\\\/].*")) throw refused();
        if (base.startsWith("~/") || base.startsWith("~\\")) {
            String userHome = System.getProperty("user.home");
            if (userHome == null || userHome.isEmpty()) throw refused();
            base = userHome + base.substring(1);
        }
        return base;
    }

    private static boolean exists(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true; // Also refuse a directory or dangling symlink occupying the other format's name.
        } catch (NoSuchFileException missing) { return false; }
    }

    private static SQLException refused() {
        return new SQLException("Local H2 file format is incompatible or ambiguous; offline migration is required");
    }
}
