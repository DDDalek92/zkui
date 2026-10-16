package com.deem.zkui.test;

import com.deem.zkui.utils.StartupSecurity;
import com.deem.zkui.utils.TrustedProxyCustomizer;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class StartupSecurityTest {
    private Properties users(String value) { Properties p = new Properties(); p.setProperty("userSet", value); return p; }

    @Test public void samplesRequireExplicitOptInAndCustomCredentialsRemainValid() {
        Properties p = users("{\"users\":[{\"username\":\"admin\",\"password\":\"manager\",\"role\":\"ADMIN\"}]}");
        assertThrows(IllegalArgumentException.class, () -> StartupSecurity.validateUsers(p));
        p.setProperty("allowSampleUsers", "true");
        assertTrue(StartupSecurity.validateUsers(p));
        assertFalse(StartupSecurity.validateUsers(users("{\"users\":[{\"username\":\"admin\",\"password\":\"synthetic-unique\",\"role\":\"ADMIN\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> StartupSecurity.validateUsers(users("{\"users\":[{\"username\":\"appconfig\",\"password\":\"appconfig\",\"role\":\"USER\"}]}")));
    }

    @Test public void invalidBlankAndDuplicateUsersFailWithoutLeakingJson() {
        for (String value : new String[]{"synthetic-secret-json", "{\"users\":[]}", "{\"users\":[{\"username\":\"one\",\"password\":\" \",\"role\":\"ADMIN\"}]}",
                "{\"users\":[{\"username\":\"same\",\"password\":\"first\",\"role\":\"USER\"},{\"username\":\"same\",\"password\":\"second\",\"role\":\"ADMIN\"}]}"}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> StartupSecurity.validateUsers(users(value)));
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("synthetic-secret-json"));
        }
    }

    @Test public void ldapDoesNotEnableUnusedLocalSampleAccounts() {
        Properties p = users("unused sample credentials"); p.setProperty("ldapAuth", "true");
        assertFalse(StartupSecurity.validateUsers(p));
    }

    @Test public void proxyPeersRequireNumericAddressesAndNoWildcardsOrHostnames() {
        for (String value : new String[]{"", "*", "localhost", "127.0.0.1/8", "127.0.0.1,", "999.0.0.1", "127.00.0.1", "bad:ipv6"}) {
            Properties p = new Properties(); p.setProperty("trustedProxyAddresses", value);
            assertThrows(IllegalArgumentException.class, () -> new TrustedProxyCustomizer(p));
        }
        Properties p = new Properties(); p.setProperty("trustedProxyAddresses", "127.0.0.1, ::1"); new TrustedProxyCustomizer(p);
    }
}
