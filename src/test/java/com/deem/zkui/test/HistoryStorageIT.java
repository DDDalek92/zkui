package com.deem.zkui.test;

import com.deem.zkui.dao.Dao;
import com.deem.zkui.domain.History;
import java.util.List;
import java.util.Properties;
import org.javalite.activejdbc.Base;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class HistoryStorageIT {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @org.junit.After public void closeLeakedTestConnection() { if (Base.hasConnection()) Base.close(); }

    private Properties config() throws Exception {
        Properties props = new Properties();
        props.setProperty("env", "prod");
        props.setProperty("jdbcClass", "org.h2.Driver");
        props.setProperty("jdbcUrl", "jdbc:h2:" + temporary.newFolder().getAbsolutePath() + "/history");
        props.setProperty("jdbcUser", "sa");
        props.setProperty("jdbcPwd", "");
        return props;
    }

    @Test public void configuredHistoryLimitAppliesToAllAndFilteredQueries() throws Exception {
        Properties props = config();
        props.setProperty("historyLimit", "2");
        Dao dao = new Dao(props);
        dao.checkNCreate();
        for (int i = 1; i <= 4; i++) dao.insertHistory("test", "127.0.0.1", "history-fixture-" + i);
        List<History> all = dao.fetchHistoryRecords();
        assertEquals(2, all.size());
        assertEquals("history-fixture-4", all.get(0).getChangeSummary());
        assertEquals("history-fixture-3", all.get(1).getChangeSummary());
        assertEquals(2, dao.fetchHistoryRecordsByNode("%history-fixture-%").size());
        assertFalse(Base.hasConnection());
        assertEquals(0, dao.fetchHistoryRecordsByNode("%' OR 1=1 --%").size());
        assertFalse(Base.hasConnection());
    }

    @Test public void databaseFailuresDoNotLeakThreadLocalConnections() throws Exception {
        Properties props = config();
        Dao dao = new Dao(props);
        dao.checkNCreate();
        dao.open();
        Base.exec("DROP TABLE HISTORIES");
        dao.close();
        try { dao.fetchHistoryRecords(); fail("Missing table must fail"); }
        catch (org.javalite.activejdbc.DBException expected) { }
        assertFalse("Failed reads must close the connection", Base.hasConnection());
        dao.insertHistory("test", "127.0.0.1", "fails-on-missing-table");
        assertFalse("Failed inserts must close the connection", Base.hasConnection());
        Dao recovery = new Dao(config());
        recovery.checkNCreate();
        recovery.insertHistory("test", "127.0.0.1", "connection-recovered");
        assertEquals(1, recovery.fetchHistoryRecords().size());
        assertFalse(Base.hasConnection());
    }

    @Test public void omittedEnvironmentDoesNotWipeExistingHistory() throws Exception {
        Properties props = config();
        props.remove("env");
        Dao dao = new Dao(props);
        dao.checkNCreate();
        dao.insertHistory("test", "127.0.0.1", "preserved-across-startup");
        dao.checkNCreate();
        assertEquals(1, dao.fetchHistoryRecords().size());
    }
}
