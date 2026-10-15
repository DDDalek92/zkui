package com.deem.zkui.dao;

import java.nio.file.*;
import java.sql.Driver;
import java.sql.SQLException;

/** Isolated candidate driver preflight only; deliberately never opens a database. */
public final class H2FileFormatGuardProbe {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath();
        if (!"owned-format-guard".equals(Files.readString(root.resolve("owned-fixture"))))
            throw new IllegalStateException("Owned fixture marker required");
        Driver driver = (Driver) Class.forName("org.h2.Driver").getDeclaredConstructor().newInstance();
        boolean legacy = args.length > 1 && "legacy".equals(args[1]);
        if (driver.getMajorVersion() != (legacy ? 1 : 2) || driver.getMinorVersion() != (legacy ? 3 : 5))
            throw new IllegalStateException("Candidate driver required");
        H2FileFormatGuard.check("org.h2.Driver", "jdbc:h2:" + root.resolve("fresh"));
        try {
            H2FileFormatGuard.check("org.h2.Driver", "jdbc:h2:" + root.resolve("history"));
            throw new AssertionError("Candidate must refuse old format before opening it");
        } catch (SQLException expected) {
            System.out.println(legacy ? "LEGACY_GUARD_REFUSED_MODERN_FILE_WITHOUT_CONNECTION"
                    : "CANDIDATE_GUARD_REFUSED_OLD_FILE_WITHOUT_CONNECTION");
        }
        if (legacy) {
            System.setProperty("h2.mvStore", "true");
            try {
                H2FileFormatGuard.check("org.h2.Driver", "jdbc:h2:" + root.resolve("fresh"));
                throw new AssertionError("Unvalidated old-engine MV_STORE switch must be refused");
            } catch (SQLException expected) { System.out.println("LEGACY_MV_STORE_SETTING_REFUSED_WITHOUT_CONNECTION"); }
        }
    }
}
