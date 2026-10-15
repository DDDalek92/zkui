package com.deem.zkui.test;
import com.deem.zkui.utils.EnvironmentConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;
public class EnvironmentConfigTest {
    @Test public void overridesPreserveSpecialCharactersAndDoNotMutateTheirSource() {
        Properties props = new Properties(); props.setProperty("zkServer", "original");
        EnvironmentConfig.apply(props, java.util.Collections.emptyMap());
        assertEquals("original", props.getProperty("zkServer"));
        Map<String, String> env = new HashMap<>();
        env.put("ZK_SERVER", "one:2181,two:2181/chroot");
        env.put("USER_SET", "{\\\"example\\\":\\\"a&b/\\\\c\\\"}");
        env.put("LOGIN_MESSAGE", "中文 & / \\"); env.put("ZKUI_BASEURL", "/tools/zkui/");
        EnvironmentConfig.apply(props, env);
        assertEquals(env.get("ZK_SERVER"), props.getProperty("zkServer"));
        assertEquals(env.get("USER_SET"), props.getProperty("userSet"));
        assertEquals(env.get("LOGIN_MESSAGE"), props.getProperty("loginMessage"));
        assertEquals("/tools/zkui/", props.getProperty("baseURL"));
        assertEquals(4, env.size());
    }
}
