/**
 *
 * Copyright (c) 2014, Deem Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.deem.zkui.dao;

import com.deem.zkui.domain.History;
import com.googlecode.flyway.core.Flyway;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.slf4j.LoggerFactory;

public class Dao {
    
    private final static org.slf4j.Logger logger = LoggerFactory.getLogger(Dao.class);
    private final Properties globalProps;
    private final int historyLimit;
    
    public Dao(Properties globalProps) {
        this.globalProps = globalProps;
        String configured = globalProps.getProperty("historyLimit", "50").trim();
        try {
            historyLimit = Integer.parseInt(configured);
            if (historyLimit < 1 || historyLimit > 1000) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("historyLimit must be between 1 and 1000");
        }
    }
    
    public int getHistoryLimit() { return historyLimit; }

    protected Connection openConnection() throws SQLException {
        try {
            Class.forName(globalProps.getProperty("jdbcClass"));
        } catch (ClassNotFoundException ex) {
            throw new SQLException("Configured JDBC driver is unavailable", ex);
        }
        return DriverManager.getConnection(globalProps.getProperty("jdbcUrl"),
                globalProps.getProperty("jdbcUser"), globalProps.getProperty("jdbcPwd"));
    }
    
    public void checkNCreate() {
        try {
            Flyway flyway = new Flyway();
            flyway.setValidateOnMigrate(true);
            flyway.setDataSource(new com.googlecode.flyway.core.util.jdbc.DriverDataSource(
                    globalProps.getProperty("jdbcClass"), globalProps.getProperty("jdbcUrl"),
                    globalProps.getProperty("jdbcUser"), globalProps.getProperty("jdbcPwd")));
            try (Connection connection = openConnection()) { HistoryMigrations.configure(flyway, connection); }
            //Will wipe db each time. Avoid this in prod.
            if ("dev".equals(globalProps.getProperty("env", "prod"))) {
                flyway.clean();
            }
            //Remove the above line if deploying to prod.
            try (Connection connection = openConnection()) { HistoryMigrations.configure(flyway, connection); }
            flyway.migrate();
            // A migration record alone does not prove the application table is usable.
            try (Connection connection = openConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT ID, CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP FROM histories WHERE 1 = 0");
                    ResultSet ignored = statement.executeQuery()) {
                // Validate the exact columns used by the DAO before HTTP starts.
            }
        } catch (Exception ex) {
            // Driver/migration messages may contain credentials or SQL data. Do not chain or print them.
            logger.error("History database initialization failed; HTTP server will not start");
            throw new IllegalStateException("History database initialization failed; HTTP server was not started");
        }
        
    }
    
    public List<History> fetchHistoryRecords() {
        return fetchHistory(null, false);
    }

    public List<History> fetchHistoryRecordsByNode(String historyNode) {
        return fetchHistory(historyNode, true);
    }

    private List<History> fetchHistory(String filter, boolean filtered) {
        String sql = "SELECT ID, CHANGE_USER, CHANGE_DATE, CHANGE_SUMMARY, CHANGE_IP FROM histories"
                + (filtered ? " WHERE CHANGE_SUMMARY LIKE ?" : "") + " ORDER BY ID DESC LIMIT ?";
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            if (filtered) statement.setString(parameter++, filter);
            statement.setInt(parameter, historyLimit);
            List<History> history = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    History row = new History();
                    row.setId(rows.getLong("ID"));
                    row.setChangeUser(rows.getString("CHANGE_USER"));
                    row.setChangeDate(rows.getTimestamp("CHANGE_DATE"));
                    row.setChangeSummary(rows.getString("CHANGE_SUMMARY"));
                    row.setChangeIp(rows.getString("CHANGE_IP"));
                    history.add(row);
                }
            }
            return history;
        } catch (SQLException ex) {
            throw new IllegalStateException("History query failed", ex);
        }
    }

    public static final class HistoryWriteException extends IllegalStateException {
        public HistoryWriteException() { super("History write failed"); }
    }

    public void insertHistory(String user, String ipAddress, String summary) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO histories (CHANGE_USER, CHANGE_IP, CHANGE_SUMMARY, CHANGE_DATE) VALUES (?, ?, ?, ?)")) {
            //To avoid errors due to truncation.
            if (summary.length() >= 500) {
                summary = summary.substring(0, 500);
            }
            statement.setString(1, user);
            statement.setString(2, ipAddress);
            statement.setString(3, summary);
            statement.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            statement.executeUpdate();
        } catch (Exception ex) {
            // The ZK operation may already have succeeded. Report the audit failure explicitly.
            logger.error("History insert failed");
            throw new HistoryWriteException();
        }
        
    }
}
