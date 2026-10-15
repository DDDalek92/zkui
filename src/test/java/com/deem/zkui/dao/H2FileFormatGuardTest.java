package com.deem.zkui.dao;

import java.io.File;
import java.nio.file.*;
import java.sql.SQLException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class H2FileFormatGuardTest {
    // Keep relative-path fixtures on the checkout's drive on Windows as well.
    @Rule public TemporaryFolder temporary = new TemporaryFolder(new File("target"));

    private void refused(String driver, String url) throws Exception {
        try { H2FileFormatGuard.check(driver, url); fail("Ambiguous storage must be refused"); }
        catch (SQLException expected) {
            assertEquals("Local H2 file format is incompatible or ambiguous; offline migration is required", expected.getMessage());
            assertNull(expected.getCause());
        }
    }

    @Test public void checksAbsoluteAndRelativeBasesBeforeSettingsAndRejectsSuffixedUrls() throws Exception {
        Path base = temporary.newFolder().toPath().resolve("history");
        Files.writeString(Path.of(base + ".mv.db"), "owned-marker");
        refused("org.h2.Driver", "jdbc:h2:" + base + ";PASSWORD=synthetic-secret");
        String relative = Path.of("").toAbsolutePath().relativize(base.toAbsolutePath()).toString();
        refused("org.h2.Driver", "jdbc:h2:file:./" + relative);
        refused("org.h2.Driver", "jdbc:h2:" + base + ".mv.db");
        refused("org.h2.Driver", "jdbc:h2:" + base + ".h2.db");
        refused("org.h2.Driver", "jdbc:h2:" + base + ".MV.DB");
        refused("org.h2.Driver", "jdbc:h2:" + base + ".H2.DB");
        assertEquals("owned-marker", Files.readString(Path.of(base + ".mv.db")));
        assertFalse(Files.exists(Path.of(base + ".h2.db")));
    }

    @Test public void freshAndExistingPageStoreAreAllowedWithoutOpeningEither() throws Exception {
        Path base = temporary.newFolder().toPath().resolve("history");
        H2FileFormatGuard.check("org.h2.Driver", "jdbc:h2:" + base);
        assertFalse(Files.exists(Path.of(base + ".h2.db")));
        Files.writeString(Path.of(base + ".h2.db"), "owned-old-marker");
        H2FileFormatGuard.check("org.h2.Driver", "jdbc:h2:file:" + base);
        assertEquals("owned-old-marker", Files.readString(Path.of(base + ".h2.db")));
    }

    @Test public void nonLocalUrlsDoNotPretendToBeInspectedAndOtherBackendsRemainUnchanged() throws Exception {
        for (String url : new String[]{"jdbc:h2:mem:owned", "jdbc:h2:tcp://127.0.0.1/owned",
                "jdbc:h2:ssl://127.0.0.1/owned", "jdbc:h2:zip:owned.zip!/owned"})
            H2FileFormatGuard.check("org.h2.Driver", url); // This method must never connect to these URLs.
        H2FileFormatGuard.check("com.mysql.cj.jdbc.Driver", "jdbc:mysql://127.0.0.1/owned");
        refused("other.Driver", "jdbc:h2:./history");
        refused("org.h2.Driver", "jdbc:mysql://127.0.0.1/owned");
    }

    @Test public void refusesVirtualFilesystemsAndAmbiguousEscapedSettingsWithoutConnecting() throws Exception {
        for (String path : new String[]{"nio:/owned/history", "split:4:/owned/history", "file:nio:/owned/history",
                "./owned\\;PASSWORD=synthetic-secret", ""})
            refused("org.h2.Driver", "jdbc:h2:" + path);
    }

    @Test public void homeAbbreviationChecksTheExpandedBase() throws Exception {
        String previous = System.getProperty("user.home");
        Path owned = temporary.newFolder().toPath();
        Files.writeString(owned.resolve("history.mv.db"), "owned-marker");
        try {
            System.setProperty("user.home", owned.toString());
            refused("org.h2.Driver", "jdbc:h2:~/history");
            refused("org.h2.Driver", "jdbc:h2:file:~/history");
        } finally {
            if (previous == null) System.clearProperty("user.home"); else System.setProperty("user.home", previous);
        }
    }

    @Test public void otherFormatNameOccupiedByDirectoryIsNotTreatedAsMissing() throws Exception {
        Path base = temporary.newFolder().toPath().resolve("history");
        Files.createDirectory(Path.of(base + ".mv.db"));
        refused("org.h2.Driver", "jdbc:h2:" + base);
    }

    @Test public void explicitStorageSwitchCannotBypassDefaultFormatPreflight() throws Exception {
        Path base = temporary.newFolder().toPath().resolve("history");
        refused("org.h2.Driver", "jdbc:h2:" + base + ";mv_store=TRUE");
        refused("org.h2.Driver", "jdbc:h2:" + base + ";MV_STORE=FALSE");
        String previous = System.getProperty("h2.mvStore");
        try {
            System.setProperty("h2.mvStore", "true");
            refused("org.h2.Driver", "jdbc:h2:" + base);
        } finally {
            if (previous == null) System.clearProperty("h2.mvStore"); else System.setProperty("h2.mvStore", previous);
        }
        assertFalse(Files.exists(Path.of(base + ".h2.db")));
        assertFalse(Files.exists(Path.of(base + ".mv.db")));
    }
}
