package com.deem.zkui.test;
import com.deem.zkui.dao.Dao;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;
public class HistoryConfigurationTest {
    @Test public void limitsAreBoundedAndInvalidValuesAreNotEchoed() {
        assertEquals(50, new Dao(new Properties()).getHistoryLimit());
        for (String input : new String[]{"0", "1001", "-1", "invalid-secret", "2147483648", ""}) {
            Properties props = new Properties();
            props.setProperty("historyLimit", input);
            try { new Dao(props); fail("Invalid limit accepted"); }
            catch (IllegalArgumentException expected) { assertFalse(expected.getMessage().contains("invalid-secret")); }
        }
    }
}
