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
        assertEquals(0, run(root, "seed", false).status);
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
        Process process = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                "-Duser.timezone=UTC", "-Dfile.encoding=UTF-8", "-cp", classpath,
                H2MigrationProbe.class.getName(), mode, root.toString(), nonce)
                .redirectErrorStream(true).redirectOutput(log).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError("Owned migration subprocess timed out");
        }
        Reply reply = new Reply(); reply.status = process.exitValue(); reply.output = Files.readString(log.toPath());
        return reply;
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
}
