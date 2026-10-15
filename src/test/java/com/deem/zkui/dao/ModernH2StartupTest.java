package com.deem.zkui.dao;

import java.sql.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ModernH2StartupTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Properties config() throws Exception {
        Properties props = new Properties();
        props.setProperty("jdbcUrl", "jdbc:h2:" + temporary.newFolder().getAbsolutePath() + "/history");
        props.setProperty("jdbcUser", "sa"); props.setProperty("jdbcPwd", "");
        return props;
    }

    @Test public void freshDatabaseMetadataIsValidOnRepeatedBootstrap() throws Exception {
        Properties props = config();
        ModernH2History.migrate(props);
        try (Connection connection = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", "")) {
            List<String> actual = new ArrayList<>();
            try (ResultSet columns = connection.getMetaData().getColumns(null, "PUBLIC", "HISTORIES", "%")) {
                while (columns.next()) actual.add(columns.getString("COLUMN_NAME") + ":" + columns.getInt("DATA_TYPE")
                        + ":" + columns.getInt("NULLABLE") + ":" + columns.getString("IS_AUTOINCREMENT"));
            }
            assertEquals(List.of("ID:-5:0:YES", "CHANGE_USER:12:1:NO", "CHANGE_DATE:93:1:NO",
                    "CHANGE_SUMMARY:12:1:NO", "CHANGE_IP:12:1:NO"), actual);
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM \"schema_version\"")) {
                // Flyway13 creates its own H2 TABLE marker at installed_rank=-1, in addition to V1.
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1));
            }
        }
        ModernH2History.migrate(props);
    }

    @Test public void unknownFutureVersionAndTamperedTableMarkerAreRefusedWithoutRepairOrDevClean() throws Exception {
        for (boolean future : new boolean[]{true, false}) {
            Properties props = config();
            ModernH2History.migrate(props);
            String sql = future ? "INSERT INTO \"schema_version\" SELECT 2, '2', \"description\", \"type\", \"script\", \"checksum\", \"installed_by\", \"installed_on\", \"execution_time\", \"success\" FROM \"schema_version\" WHERE \"version\"='1'"
                    : "UPDATE \"schema_version\" SET \"script\"='unknown' WHERE \"installed_rank\"=-1";
            try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", ""); Statement s = c.createStatement()) { assertEquals(1, s.executeUpdate(sql)); }
            props.setProperty("env", "dev");
            try { ModernH2History.migrate(props); fail("Unknown metadata must not be cleaned or ignored"); }
            catch (SQLException expected) { assertEquals("Unrecognized migration history", expected.getMessage()); }
            try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", "");
                    Statement s = c.createStatement(); ResultSet rows = s.executeQuery(future
                            ? "SELECT COUNT(*) FROM \"schema_version\" WHERE \"version\"='2'"
                            : "SELECT COUNT(*) FROM \"schema_version\" WHERE \"script\"='unknown'")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test public void metadataChecksumAloneCannotAcceptWidenedSchemaOrTriggerDevClean() throws Exception {
        Properties props = config();
        ModernH2History.migrate(props);
        try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", ""); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE histories ALTER COLUMN change_summary VARCHAR(501)");
        }
        props.setProperty("env", "dev");
        try { ModernH2History.migrate(props); fail("Schema drift must fail before clean"); }
        catch (SQLException expected) { assertEquals("Historical schema does not match the validated contract", expected.getMessage()); }
        try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", "");
                ResultSet column = c.getMetaData().getColumns(null, "PUBLIC", "HISTORIES", "CHANGE_SUMMARY")) {
            assertTrue(column.next()); assertEquals(501, column.getInt("COLUMN_SIZE"));
        }
    }

    @Test public void developmentFlagDoesNotEraseExistingHistoryOrMigrationRecords() throws Exception {
        Properties props = config();
        ModernH2History.migrate(props);
        int checksum;
        try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", ""); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO histories(change_summary) VALUES('owned-development-preservation')");
            try (ResultSet rows = s.executeQuery("SELECT \"checksum\" FROM \"schema_version\" WHERE \"version\"='1'")) {
                assertTrue(rows.next()); checksum = rows.getInt(1);
            }
        }
        props.setProperty("env", "dev");
        ModernH2History.migrate(props);
        try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", ""); Statement s = c.createStatement()) {
            try (ResultSet rows = s.executeQuery("SELECT change_summary FROM histories")) {
                assertTrue(rows.next()); assertEquals("owned-development-preservation", rows.getString(1)); assertFalse(rows.next());
            }
            try (ResultSet rows = s.executeQuery("SELECT \"checksum\" FROM \"schema_version\" WHERE \"version\"='1'")) {
                assertTrue(rows.next()); assertEquals(checksum, rows.getInt(1));
            }
        }
    }

    @Test public void explicitSourceTimezonePreservesTimestampInstantIndependentOfCachedJvmDefault() throws Exception {
        Properties props = config();
        props.setProperty("jdbcUrl", props.getProperty("jdbcUrl") + ";TIME ZONE=UTC");
        ModernH2History.migrate(props);
        try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", ""); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO histories(change_date) VALUES(TIMESTAMP '2023-11-14 22:13:20.123')");
            try (ResultSet rows = s.executeQuery("SELECT change_date FROM histories")) {
                assertTrue(rows.next()); assertEquals(java.time.Instant.ofEpochMilli(1700000000123L), rows.getTimestamp(1).toInstant());
            }
        }
    }

    @Test public void dataInAnotherSchemaCannotBeMistakenForAnEmptyNewDatabase() throws Exception {
        Properties props = config();
        try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", ""); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA owned_other");
            s.execute("CREATE TABLE owned_other.owned_evidence(id INT PRIMARY KEY)");
            s.executeUpdate("INSERT INTO owned_other.owned_evidence VALUES(41)");
        }
        props.setProperty("env", "dev");
        try { ModernH2History.migrate(props); fail("Unknown data must not be abandoned by creating a fresh history schema"); }
        catch (SQLException expected) { assertEquals("Unrecognized nonempty history schema", expected.getMessage()); }
        try (Connection c = DriverManager.getConnection(ModernH2History.url(props.getProperty("jdbcUrl")), "sa", "");
                Statement s = c.createStatement(); ResultSet rows = s.executeQuery("SELECT id FROM owned_other.owned_evidence")) {
            assertTrue(rows.next()); assertEquals(41, rows.getInt(1)); assertFalse(rows.next());
            try (ResultSet tables = c.getMetaData().getTables(null, "PUBLIC", "%", new String[]{"TABLE"})) { assertFalse(tables.next()); }
        }
    }
}
