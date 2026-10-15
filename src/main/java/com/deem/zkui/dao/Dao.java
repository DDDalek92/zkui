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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.javalite.activejdbc.Base;
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

    public void open() {
        Base.open(globalProps.getProperty("jdbcClass"), globalProps.getProperty("jdbcUrl"), globalProps.getProperty("jdbcUser"), globalProps.getProperty("jdbcPwd"));
    }
    
    public void close() {
        if (Base.hasConnection()) Base.close();
    }
    
    public void checkNCreate() {
        try {
            Flyway flyway = new Flyway();
            flyway.setDataSource(globalProps.getProperty("jdbcUrl"), globalProps.getProperty("jdbcUser"), globalProps.getProperty("jdbcPwd"));
            //Will wipe db each time. Avoid this in prod.
            if ("dev".equals(globalProps.getProperty("env", "prod"))) {
                flyway.clean();
            }
            //Remove the above line if deploying to prod.
            flyway.migrate();
        } catch (Exception ex) {
            logger.error("Error trying to migrate db! Not severe hence proceeding forward.");
        }
        
    }
    
    public List<History> fetchHistoryRecords() {
        try {
            this.open();
            List<History> history = History.findAll().orderBy("ID desc").limit(historyLimit);
            history.size();
            return history;
        } finally {
            this.close();
        }
    }

    public List<History> fetchHistoryRecordsByNode(String historyNode) {
        try {
            this.open();
            List<History> history = History.where("CHANGE_SUMMARY like ?", historyNode).orderBy("ID desc").limit(historyLimit);
            history.size();
            return history;
        } finally {
            this.close();
        }
    }

    public void insertHistory(String user, String ipAddress, String summary) {
        try {
            this.open();
            //To avoid errors due to truncation.
            if (summary.length() >= 500) {
                summary = summary.substring(0, 500);
            }
            History history = new History();
            history.setChangeUser(user);
            history.setChangeIp(ipAddress);
            history.setChangeSummary(summary);
            history.setChangeDate(new Date());
            history.save();
        } catch (Exception ex) {
            logger.error(Arrays.toString(ex.getStackTrace()));
        } finally {
            this.close();
        }
        
    }
}
