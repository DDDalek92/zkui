package com.deem.zkui.test;

import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.sql.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Never opens external databases: all inputs, credentials, paths and subprocesses are test-owned. */
public class H2MigrationRehearsalIT {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final String nonce = UUID.randomUUID().toString();

    private Path fixture() throws Exception {
        Path root = temporary.newFolder().toPath();
        Files.writeString(root.resolve("owned-fixture"), nonce);
        Reply seed = run(root, "seed", false);
        assertEquals(seed.output, 0, seed.status);
        return root;
    }

    private static final class Reply {
        int status;
        String output;
    }
    private Reply run(Path root, String mode, boolean candidate) throws Exception {
        return runWithClasspath(root, mode, candidate, false);
    }
    private Reply runWithClasspath(Path root, String mode, boolean candidate, boolean bridge) throws Exception {
        Process process = startWithClasspath(root, mode, candidate, bridge);
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new AssertionError("Owned migration subprocess timed out");
            Reply reply = new Reply(); reply.status = process.exitValue(); reply.output = Files.readString(root.resolve(mode + ".log"));
            return reply;
        } finally {
            stopOwnedProcess(process);
        }
    }
    private void stopOwnedProcess(Process process) throws Exception {
        if (process.isAlive()) {
            process.destroyForcibly();
            assertTrue("Owned subprocess must terminate", process.waitFor(5, TimeUnit.SECONDS));
        }
    }
    private Process startWithClasspath(Path root, String mode, boolean candidate, boolean bridge) throws Exception {
        String classes = new File(H2MigrationProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        String driver = new File(System.getProperty(candidate ? "zkui.migration.h2.jar" : "zkui.migration.old.h2.jar")).getAbsolutePath();
        assertTrue("Required isolated driver/app artifact missing", new File(driver).isFile());
        String classpath = classes + File.pathSeparator + driver
                + File.pathSeparator + new File("target/classes").getAbsolutePath();
        if (!candidate) classpath += File.pathSeparator + new File(System.getProperty("zkui.migration.old.flyway.jar")).getAbsolutePath();
        if (bridge) classpath += File.pathSeparator + new File(System.getProperty("zkui.migration.bridge.jar")).getAbsolutePath();
        if ("modern".equals(mode)) {
            // Use test dependencies, excluding the old H2; never include the assembled application JAR.
            String source = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            java.util.List<String> entries = new java.util.ArrayList<>();
            for (String entry : source.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (!new File(entry).getName().startsWith("h2-") && !entry.endsWith("jar-with-dependencies.jar")) entries.add(entry);
            }
            entries.add(driver);
            classpath = String.join(File.pathSeparator, entries);
        }
        File log = root.resolve(mode + ".log").toFile();
        return new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-cp", classpath,
                H2MigrationProbe.class.getName(), mode, root.toString(), nonce)
                .redirectErrorStream(true).redirectOutput(log).start();
    }
    private Map<String, String> sealedManifest(Path root) throws Exception {
        return directoryManifest(root.resolve("sealed-source"));
    }

    @Test public void genuineBridgedHistoryRunsInPackagedMainAndSurvivesApplicationRestart() throws Throwable {
        // Explicit JDBC session and child-app UTC avoid dependence on a cached JVM default timezone.
        // A naive TIMESTAMP is not a portable Instant without an agreed source/target timezone.
        runBridgedApplication();
    }

    private void runBridgedApplication() throws Throwable {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        byte[] exportBefore = Files.readAllBytes(root.resolve("export.sql"));
        Reply bridge = runWithClasspath(root, "bridge", false, true);
        assertEquals(bridge.output, 0, bridge.status);
        Reply imported = run(root, "application-import", true);
        assertEquals(imported.output, 0, imported.status);
        TestApplication app = new TestApplication("modern-bridged-application",
                () -> new String[]{"baseURL", "/migrated/", "jdbcUser", "sa", "jdbcPwd", "", "testTimeZone", "UTC", "testHistoryUtc", "true"},
                base -> Files.copy(root.resolve("new-application/history.mv.db"), Path.of(base + ".mv.db")));
        app.before();
        try {
            String expectedMetadata = Files.readString(root.resolve("expected-bridge-metadata"));
            try (Connection connection = DriverManager.getConnection(app.historyJdbcUrl(), "sa", "")) {
                assertTrue(connection.getMetaData().getDatabaseProductVersion().startsWith("2.5.252 "));
                assertEquals(Files.readString(root.resolve("expected-base-history")),
                        H2MigrationProbe.snapshotRows(connection, "SELECT * FROM histories ORDER BY ID"));
                assertEquals(expectedMetadata, H2MigrationProbe.snapshotRows(connection, "SELECT * FROM \"schema_version\""));
            }
            String admin = app.login("test-admin");
            TestApplication.Reply page = app.get("/history", admin);
            assertEquals(200, page.status);
            assertTrue(page.body.contains("<td>41</td>"));
            assertTrue(page.body.contains("<td>9007199254740993</td>"));
            assertTrue(page.body.contains("中文😀"));
            assertTrue(page.body.contains("action=\"/migrated/history\""));
            TestApplication.Reply filtered = app.post("/history", "action=showhistory&historyNode="
                    + java.net.URLEncoder.encode("中文😀", java.nio.charset.StandardCharsets.UTF_8), admin);
            assertEquals(200, filtered.status);
            assertTrue(filtered.body.contains("<td>9007199254740993</td>"));
            assertFalse(filtered.body.contains("<td>41</td>"));
            assertFalse(filtered.body.contains("<td>9007199254741003</td>"));
            String user = app.login("test-user");
            assertEquals(403, app.get("/history", user).status);
            assertEquals(403, app.post("/history", "action=showhistory&historyNode=", user).status);
            assertEquals(302, app.post("/home", "action=Save+Property&currentPath=/appconfig/&displayPath=/appconfig"
                    + "&newProperty=after-migration&newValue=owned", admin).status);
            assertEquals("owned", app.read("/appconfig/after-migration"));
            String rowsAfter;
            try (Connection connection = DriverManager.getConnection(app.historyJdbcUrl(), "sa", "");
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT MAX(ID), COUNT(*) FROM histories")) {
                assertTrue(rows.next());
                assertTrue(rows.getLong(1) > H2MigrationProbe.HIGH_ID + 10);
                assertEquals(4, rows.getInt(2));
                rowsAfter = H2MigrationProbe.snapshotRows(connection, "SELECT * FROM histories ORDER BY ID");
            }
            app.restart();
            assertEquals(200, app.get("/history", app.login("test-admin")).status);
            try (Connection connection = DriverManager.getConnection(app.historyJdbcUrl(), "sa", "")) {
                assertEquals(rowsAfter, H2MigrationProbe.snapshotRows(connection, "SELECT * FROM histories ORDER BY ID"));
                assertEquals(expectedMetadata, H2MigrationProbe.snapshotRows(connection, "SELECT * FROM \"schema_version\""));
            }
        } finally { app.after(); }
        assertArrayEquals(exportBefore, Files.readAllBytes(root.resolve("export.sql")));
        assertEquals(before, sealedManifest(root));
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
    }

    @Test public void unbridgedMetadataCannotBeAutoConvertedByDaoOrPackagedMain() throws Throwable {
        Path root = fixture();
        Map<String, String> sealed = sealedManifest(root);
        byte[] export = Files.readAllBytes(root.resolve("export.sql"));
        Reply imported = run(root, "application-import-unbridged", true);
        assertEquals(imported.output, 0, imported.status);
        String url = H2MigrationProbe.url(root, "new-application") + ";MODE=LEGACY;IFEXISTS=TRUE";
        String metadata, history;
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            metadata = H2MigrationProbe.snapshotRows(c, "SELECT * FROM \"schema_version\"");
            history = H2MigrationProbe.snapshotRows(c, "SELECT * FROM histories ORDER BY ID");
        }
        Properties props = new Properties();
        props.setProperty("jdbcClass", "org.h2.Driver"); props.setProperty("jdbcUrl", url);
        props.setProperty("jdbcUser", "sa"); props.setProperty("jdbcPwd", "");
        props.setProperty("env", "dev");
        try { new com.deem.zkui.dao.Dao(props).checkNCreate(); fail("Unbridged metadata must be refused before any conversion/clean"); }
        catch (IllegalStateException expected) {
            assertEquals("History database initialization failed; HTTP server was not started", expected.getMessage());
            assertNull(expected.getCause());
        }
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(metadata, H2MigrationProbe.snapshotRows(c, "SELECT * FROM \"schema_version\""));
            assertEquals(history, H2MigrationProbe.snapshotRows(c, "SELECT * FROM histories ORDER BY ID"));
            try (ResultSet columns = c.getMetaData().getColumns(null, "PUBLIC", "schema_version", "version_rank")) { assertTrue(columns.next()); }
        }
        TestApplication app = new TestApplication("modern-unbridged-refusal",
                () -> new String[]{"jdbcUser", "sa", "jdbcPwd", ""},
                base -> Files.copy(root.resolve("new-application/history.mv.db"), Path.of(base + ".mv.db")));
        boolean ready = false;
        try {
            try { app.before(); ready = true; }
            catch (AssertionError expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("History database initialization failed")); }
            assertFalse("Unbridged metadata must not reach HTTP readiness", ready);
        } finally { if (ready) app.after(); }
        assertEquals(sealed, sealedManifest(root));
        assertArrayEquals(export, Files.readAllBytes(root.resolve("export.sql")));
    }
    private Map<String, String> directoryManifest(Path sealed) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var files = Files.walk(sealed)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                assertFalse(Files.isSymbolicLink(path));
                result.put(sealed.relativize(path).toString(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
            }
        }
        assertFalse("Sealed source must contain the old database", result.isEmpty());
        return result;
    }


    @Test public void originalEngineRejectsTamperedChecksumBeforeBridgeCanRecalculateIt() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        assertEquals(0, run(root, "tamper", false).status);
        Reply refused = runWithClasspath(root, "bridge", false, true);
        assertNotEquals("Old validation must refuse a changed checksum", 0, refused.status);
        Reply checked = run(root, "assert-tampered", false);
        assertEquals(checked.output, 0, checked.status);
        assertTrue(checked.output.contains("TAMPERED_METADATA_REJECTED_BEFORE_BRIDGE"));
        assertEquals(before, sealedManifest(root));
    }

    @Test public void supportedFlywayBridgePreservesMetadataAndModernValidationDoesNotRepair() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        byte[] originalExport = Files.readAllBytes(root.resolve("export.sql"));
        Reply bridge = runWithClasspath(root, "bridge", false, true);
        assertEquals(bridge.output, 0, bridge.status);
        assertTrue(bridge.output.contains("FLYWAY4_SUPPORTED_METADATA_UPGRADE_OK"));
        Reply modern = run(root, "modern", true);
        assertEquals(modern.output, 0, modern.status);
        assertTrue(modern.output.contains("FLYWAY13_MIGRATE_VALIDATE_RESTART_AND_ID_OK"));
        assertEquals("Import and both Flyway restarts must check schema/constraints", 3,
                modern.output.split("HISTORY_SCHEMA_PK_INDEX_NULLABILITY_WIDTH_AND_CONSTRAINTS_OK", -1).length - 1);
        assertArrayEquals(originalExport, Files.readAllBytes(root.resolve("export.sql")));
        assertEquals(before, sealedManifest(root));
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
        assertEquals(before, sealedManifest(root));
    }

    @Test public void regularModeFailureDoesNotTouchSealedSourceAndOldCopyStillWorks() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        Reply failure = run(root, "regular", true);
        assertEquals(failure.output, 0, failure.status);
        assertTrue(failure.output.contains("EXPECTED_REGULAR_MODE_BLOCKER_DATETIME_50004"));
        assertEquals(before, sealedManifest(root));
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
        assertTrue(rollback.output.contains("OLD_DRIVER_ROLLBACK_COPY_OK"));
        assertEquals(before, sealedManifest(root));
    }

    @Test public void legacyRehearsalPreservesRowsMetadataIdsAndRejectsExistingTarget() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        Reply imported = run(root, "legacy", true);
        assertEquals(imported.output, 0, imported.status);
        assertTrue(imported.output.contains("LEGACY_ROWS_METADATA_ID_AND_REOPEN_OK"));
        Map<String, String> targetBefore = directoryManifest(root.resolve("new-legacy"));
        Reply repeated = run(root, "legacy", true);
        assertNotEquals("Existing target must never be overwritten", 0, repeated.status);
        assertEquals(targetBefore, directoryManifest(root.resolve("new-legacy")));
        assertEquals(before, sealedManifest(root));
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
        assertEquals(before, sealedManifest(root));
    }

    @Test public void truncatedImportIsNotAcceptedAndOriginalExportAndSealedSourceStayUnchanged() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        byte[] export = Files.readAllBytes(root.resolve("export.sql"));
        Reply failure = run(root, "truncated", true);
        assertEquals(failure.output, 0, failure.status);
        assertTrue(failure.output.contains("EXPECTED_TRUNCATED_SQL_SYNTAX_FAILURE"));
        assertArrayEquals(export, Files.readAllBytes(root.resolve("export.sql")));
        assertEquals(before, sealedManifest(root));
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
        assertEquals(before, sealedManifest(root));
    }

    @Test public void rowPreservingSchemaDriftIsRejectedWithoutChangingSealedSourceOrExport() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        byte[] export = Files.readAllBytes(root.resolve("export.sql"));
        Reply imported = run(root, "legacy", true);
        assertEquals(imported.output, 0, imported.status);
        Reply drift = run(root, "schema-drift", true);
        assertNotEquals("Widened schema must not pass migration acceptance", 0, drift.status);
        assertTrue(drift.output, drift.output.contains("History schema drift:"));
        assertArrayEquals(export, Files.readAllBytes(root.resolve("export.sql")));
        assertEquals(before, sealedManifest(root));
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
        assertEquals(before, sealedManifest(root));
    }

    @Test public void secondProcessRefusesFileLockAndKilledIdleHolderDoesNotLoseCommittedCandidateRows() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        byte[] export = Files.readAllBytes(root.resolve("export.sql"));
        Reply imported = run(root, "legacy", true);
        assertEquals(imported.output, 0, imported.status);
        Process holder = startWithClasspath(root, "hold-candidate-lock", true, false);
        try {
            Path ready = root.resolve("candidate-lock-ready");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (holder.isAlive() && System.nanoTime() < deadline) {
                if (Files.exists(ready) && nonce.equals(Files.readString(ready))) break;
                Thread.sleep(50);
            }
            assertTrue(Files.readString(root.resolve("hold-candidate-lock.log")), Files.exists(ready));
            assertEquals(nonce, Files.readString(ready));
            assertTrue("Holder must still own the candidate", holder.isAlive());
            Reply competitor = run(root, "compete-candidate-lock", true);
            assertEquals(competitor.output, 0, competitor.status);
            assertTrue(competitor.output, competitor.output.contains("EXPECTED_CANDIDATE_FILE_LOCK_REFUSAL_90020"));
            assertTrue("Refusal must happen while holder is still alive", holder.isAlive());
            // Kill only this test-created idle process, never another application's database owner.
            holder.destroyForcibly();
            assertTrue("Killed holder must release process resources", holder.waitFor(5, TimeUnit.SECONDS));
        } finally {
            stopOwnedProcess(holder);
        }
        Reply recovered = run(root, "recover-candidate-lock", true);
        assertEquals(recovered.output, 0, recovered.status);
        assertTrue(recovered.output, recovered.output.contains("KILLED_IDLE_HOLDER_COMMITTED_ROWS_METADATA_SCHEMA_ID_AND_REOPEN_OK"));
        assertArrayEquals(export, Files.readAllBytes(root.resolve("export.sql")));
        assertEquals(before, sealedManifest(root));
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
        assertEquals(before, sealedManifest(root));
    }

    @Test public void killedNativeRunscriptLeavesReadablePartialTargetThatCannotPassRowAcceptance() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        byte[] export = Files.readAllBytes(root.resolve("export.sql"));
        Process importer = startWithClasspath(root, "interrupt-import", true, false);
        try {
            Path ready = root.resolve("import-paused");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (importer.isAlive() && System.nanoTime() < deadline) {
                if (Files.exists(ready) && nonce.equals(Files.readString(ready))) break;
                Thread.sleep(50);
            }
            assertTrue(Files.readString(root.resolve("interrupt-import.log")), Files.exists(ready));
            assertEquals(nonce, Files.readString(ready));
            assertTrue("RUNSCRIPT must still be executing at the gate", importer.isAlive());
            importer.destroyForcibly();
            assertTrue("Owned importer must exit", importer.waitFor(5, TimeUnit.SECONDS));
        } finally {
            stopOwnedProcess(importer);
        }
        Reply partial = run(root, "check-partial-import", true);
        assertEquals(partial.output, 0, partial.status);
        assertTrue(partial.output.contains("INTERRUPTED_RUNSCRIPT_READABLE_BUT_HISTORY_MISSING_WITH_V1_PRESENT"));
        Reply refused = run(root, "accept-partial-import", true);
        assertNotEquals("Readable partial target must not pass data acceptance", 0, refused.status);
        assertTrue(refused.output, refused.output.contains("History rows or Flyway metadata differ"));
        Map<String, String> partialBefore = directoryManifest(root.resolve("new-interrupted"));
        Reply repeated = run(root, "interrupt-import", true);
        assertNotEquals("Partial target must not be overwritten or silently resumed", 0, repeated.status);
        assertEquals(partialBefore, directoryManifest(root.resolve("new-interrupted")));
        assertEquals(nonce, Files.readString(root.resolve("new-interrupted/incomplete.marker")));
        assertArrayEquals(export, Files.readAllBytes(root.resolve("export.sql")));
        assertEquals(before, sealedManifest(root));
        Reply fresh = run(root, "legacy", true); // Separate new path, never resume the partial target.
        assertEquals(fresh.output, 0, fresh.status);
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
        assertEquals(before, sealedManifest(root));
    }

    @Test public void readonlyCandidateRefusesNativeImportWithoutChangingTargetOrSourceFiles() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        byte[] export = Files.readAllBytes(root.resolve("export.sql"));
        Reply created = run(root, "create-readonly-target", true);
        assertEquals(created.output, 0, created.status);
        Map<String, String> targetBefore = directoryManifest(root.resolve("new-readonly"));
        Reply refused = run(root, "readonly-import", true);
        assertEquals(refused.output, 0, refused.status);
        assertTrue(refused.output, refused.output.contains("EXPECTED_READONLY_TARGET_RUNSCRIPT_REFUSAL_90097_NO_TABLES"));
        assertEquals(targetBefore, directoryManifest(root.resolve("new-readonly")));
        assertArrayEquals(export, Files.readAllBytes(root.resolve("export.sql")));
        assertEquals(before, sealedManifest(root));
        Reply fresh = run(root, "legacy", true);
        assertEquals(fresh.output, 0, fresh.status);
        Reply rollback = run(root, "rollback", false);
        assertEquals(rollback.output, 0, rollback.status);
        assertEquals(before, sealedManifest(root));
    }

    @Test public void appendOnlyCandidateDeltaCanBePreservedOnAnOldCopyWithoutChangingSealedSource() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        byte[] export = Files.readAllBytes(root.resolve("export.sql"));
        Reply imported = run(root, "legacy", true);
        assertEquals(imported.output, 0, imported.status);
        Reply delta = run(root, "delta-prepare", true);
        assertEquals(delta.output, 0, delta.status);
        Map<String, String> candidateBefore = directoryManifest(root.resolve("new-legacy"));
        Reply restored = run(root, "delta-restore", false);
        assertEquals(restored.output, 0, restored.status);
        assertTrue(restored.output, restored.output.contains("AUDIT_DELTA_OLD_COPY_EXACT_REPLAY_IDEMPOTENCE_ID_AND_RESTART_OK"));
        assertEquals(candidateBefore, directoryManifest(root.resolve("new-legacy")));
        assertArrayEquals(export, Files.readAllBytes(root.resolve("export.sql")));
        assertEquals(before, sealedManifest(root));
    }

    @Test public void conflictingDeltaRollsBackEarlierInsertInsteadOfOverwritingOrPartiallyRestoring() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        Reply imported = run(root, "legacy", true);
        assertEquals(imported.output, 0, imported.status);
        Reply delta = run(root, "delta-prepare", true);
        assertEquals(delta.output, 0, delta.status);
        Reply refused = run(root, "delta-conflict", false);
        assertEquals(refused.output, 0, refused.status);
        assertTrue(refused.output, refused.output.contains("AUDIT_DELTA_CONFLICT_REFUSED_ENTIRE_TRANSACTION_ROLLED_BACK"));
        assertEquals(before, sealedManifest(root));
    }

    @Test public void modifiedOrDeletedBaselineIsNotMisrepresentedAsAnAppendOnlyDelta() throws Exception {
        for (String mode : List.of("delta-change-base", "delta-delete-base")) {
            Path root = fixture();
            Map<String, String> before = sealedManifest(root);
            Reply imported = run(root, "legacy", true);
            assertEquals(imported.output, 0, imported.status);
            Reply changed = run(root, mode, true);
            assertEquals(changed.output, 0, changed.status);
            Reply refused = run(root, "delta-export", true);
            assertNotEquals("Changed/deleted original records require a separate recovery policy", 0, refused.status);
            assertTrue(refused.output, refused.output.contains("Append-only audit baseline changed or deleted"));
            assertFalse("Refusal must happen before emitting a delta", Files.exists(root.resolve("delta.bin")));
            assertEquals(before, sealedManifest(root));
            Reply rollback = run(root, "rollback", false);
            assertEquals(rollback.output, 0, rollback.status);
        }
    }

    @Test public void alteredDeltaPayloadIsRejectedBeforeCreatingOrOpeningAnOldRecoveryCopy() throws Exception {
        Path root = fixture();
        Map<String, String> before = sealedManifest(root);
        Reply imported = run(root, "legacy", true);
        assertEquals(imported.output, 0, imported.status);
        Reply prepared = run(root, "delta-prepare", true);
        assertEquals(prepared.output, 0, prepared.status);
        Path payload = root.resolve("delta.bin");
        byte[] changed = Files.readAllBytes(payload);
        changed[changed.length - 1] ^= 1; // Only this test-created transfer payload, not migration metadata.
        Files.write(payload, changed);
        Reply refused = run(root, "delta-restore", false);
        assertNotEquals("Changed payload must not be replayed", 0, refused.status);
        assertTrue(refused.output, refused.output.contains("Owned delta payload integrity failure"));
        assertFalse(Files.exists(root.resolve("delta-rollback")));
        assertEquals(before, sealedManifest(root));
    }
}
