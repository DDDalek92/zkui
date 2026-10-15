package com.deem.zkui.test;

import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
        String driver = new File(System.getProperty(candidate ? "zkui.migration.h2.jar" : "zkui.test.jar")).getAbsolutePath();
        assertTrue("Required isolated driver/app artifact missing", new File(driver).isFile());
        String classpath = classes + File.pathSeparator + driver;
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
}
