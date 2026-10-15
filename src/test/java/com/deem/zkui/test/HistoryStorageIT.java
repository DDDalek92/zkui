package com.deem.zkui.test;

import com.deem.zkui.dao.Dao;
import com.deem.zkui.domain.History;
import java.util.List;
import java.util.Properties;
import java.sql.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HistoryStorageIT {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final class TrackingDao extends Dao {
        int opened, closed;
        TrackingDao(Properties props) { super(props); }
        @Override protected Connection openConnection() throws SQLException {
            Connection real = super.openConnection();
            opened++;
            boolean[] released = {false};
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        try {
                            Object result = method.invoke(real, args);
                            if (method.getName().equals("close") && !released[0]) {
                                released[0] = true;
                                closed++;
                            }
                            return result;
                        } catch (InvocationTargetException ex) { throw ex.getCause(); }
                    });
        }
        void assertReleased() { assertEquals("Every opened JDBC connection must be closed", opened, closed); }
    }

    private Connection connect(Properties props) throws Exception {
        Class.forName(props.getProperty("jdbcClass"));
        return DriverManager.getConnection(props.getProperty("jdbcUrl"), "sa", "");
    }

    private Properties config() throws Exception {
        Properties props = new Properties();
        props.setProperty("env", "prod");
        props.setProperty("jdbcClass", "org.h2.Driver");
        // Direct fixture JDBC connections use the same per-connection legacy ID contract as the application.
        props.setProperty("jdbcUrl", "jdbc:h2:" + temporary.newFolder().getAbsolutePath() + "/history;MODE=LEGACY");
        props.setProperty("jdbcUser", "sa");
        props.setProperty("jdbcPwd", "");
        return props;
    }

    @Test public void configuredHistoryLimitAppliesToAllAndFilteredQueries() throws Exception {
        Properties props = config();
        props.setProperty("historyLimit", "2");
        TrackingDao dao = new TrackingDao(props);
        dao.checkNCreate();
        for (int i = 1; i <= 4; i++) dao.insertHistory("test", "127.0.0.1", "history-fixture-" + i);
        List<History> all = dao.fetchHistoryRecords();
        assertEquals(2, all.size());
        assertEquals("history-fixture-4", all.get(0).getChangeSummary());
        assertEquals("history-fixture-3", all.get(1).getChangeSummary());
        assertEquals(2, dao.fetchHistoryRecordsByNode("%history-fixture-%").size());
        dao.assertReleased();
        assertEquals(0, dao.fetchHistoryRecordsByNode("%' OR 1=1 --%").size());
        assertEquals(0, dao.fetchHistoryRecordsByNode(null).size());
        dao.assertReleased();
    }

    @Test public void databaseFailuresDoNotLeakConnections() throws Exception {
        Properties props = config();
        TrackingDao dao = new TrackingDao(props);
        dao.checkNCreate();
        try (Connection connection = connect(props); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE HISTORIES");
        }
        try { dao.fetchHistoryRecords(); fail("Missing table must fail"); }
        catch (IllegalStateException expected) { assertTrue(expected.getCause() instanceof SQLException); }
        dao.assertReleased();
        try { dao.insertHistory("test", "127.0.0.1", "fails-on-missing-table"); fail("Failed audit must be reported"); }
        catch (Dao.HistoryWriteException expected) { assertNull(expected.getCause()); }
        dao.assertReleased();
        TrackingDao recovery = new TrackingDao(config());
        recovery.checkNCreate();
        recovery.insertHistory("test", "127.0.0.1", "connection-recovered");
        assertEquals(1, recovery.fetchHistoryRecords().size());
        recovery.assertReleased();
    }

    @Test public void omittedEnvironmentDoesNotWipeExistingHistory() throws Exception {
        Properties props = config();
        props.remove("env");
        TrackingDao dao = new TrackingDao(props);
        dao.checkNCreate();
        dao.insertHistory("test", "127.0.0.1", "preserved-across-startup");
        dao.checkNCreate();
        assertEquals(1, dao.fetchHistoryRecords().size());
        dao.assertReleased();
    }

    private List<String> migrationMetadata(Properties props) throws Exception {
        List<String> snapshot = new ArrayList<>();
        try (Connection connection = connect(props); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT * FROM \"schema_version\"")) {
            while (rows.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                    String value = rows.getString(i);
                    row.append(value == null ? "null:" : value.length() + ":" + value).append(';');
                }
                snapshot.add(row.toString());
            }
        }
        Collections.sort(snapshot);
        return snapshot;
    }

    @Test public void freshSchemaRowsRemainDetachedAndIdsContinueWithoutChangingMetadata() throws Exception {
        Properties props = config();
        TrackingDao dao = new TrackingDao(props);
        dao.checkNCreate();
        List<String> metadata = migrationMetadata(props);
        assertFalse(metadata.isEmpty());
        long highId = 9007199254740993L;
        String summary = "  中文😀\nquote' slash\\  ";
        Timestamp date = new Timestamp(1700000000123L);
        // Seed through JDBC, not the DAO. Genuine original V1 comes from isolated old-driver bridge tests.
        try (Connection connection = connect(props); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO histories (ID, CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP) VALUES (?, ?, ?, ?, ?)")) {
            statement.setLong(1, 41L);
            statement.setString(2, null);
            statement.setTimestamp(3, null);
            statement.setString(4, "");
            statement.setString(5, "");
            statement.executeUpdate();
            statement.setLong(1, highId);
            statement.setString(2, "旧用户");
            statement.setTimestamp(3, date);
            statement.setString(4, summary);
            statement.setString(5, "127.0.0.1");
            statement.executeUpdate();
        }
        dao.checkNCreate();
        List<History> before = dao.fetchHistoryRecords();
        dao.assertReleased();
        assertEquals(2, before.size());
        assertEquals(Long.valueOf(highId), before.get(0).getId());
        assertEquals("旧用户", before.get(0).getChangeUser());
        assertEquals(date.getTime(), before.get(0).getChangeDate().getTime());
        assertEquals(summary, before.get(0).getChangeSummary());
        assertEquals("127.0.0.1", before.get(0).getChangeIp());
        assertEquals(Long.valueOf(41L), before.get(1).getId());
        assertNull(before.get(1).getChangeUser());
        assertNull(before.get(1).getChangeDate());
        assertEquals("", before.get(1).getChangeSummary());
        dao.insertHistory("test", "127.0.0.1", "x".repeat(501));
        List<History> after = dao.fetchHistoryRecords();
        assertEquals(3, after.size());
        assertTrue(after.get(0).getId() > highId);
        assertEquals("x".repeat(500), after.get(0).getChangeSummary());
        new Dao(props).checkNCreate();
        assertEquals(metadata, migrationMetadata(props));
        assertEquals(summary, before.get(0).getChangeSummary());
        dao.assertReleased();
    }
}
