package com.deem.zkui.dao;

import java.sql.SQLException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModernH2ConfigurationTest {
    @Test public void modeIsExplicitForEveryConnectionAndImplicitRelativeBaseIsNormalized() throws Exception {
        assertEquals("jdbc:h2:./zkui;MODE=LEGACY", ModernH2History.url("jdbc:h2:zkui"));
        assertEquals("jdbc:h2:file:./owned;MODE=LEGACY", ModernH2History.url("jdbc:h2:file:owned"));
        assertEquals("jdbc:h2:/owned/history;MODE=LEGACY", ModernH2History.url("jdbc:h2:/owned/history"));
        assertEquals("jdbc:h2:mem:owned;MODE=LEGACY", ModernH2History.url("jdbc:h2:mem:owned"));
        assertEquals("jdbc:h2:C:\\owned\\history;MODE=LEGACY", ModernH2History.url("jdbc:h2:C:\\owned\\history"));
        assertEquals("jdbc:h2:./owned;MODE=LEGACY;IFEXISTS=TRUE",
                ModernH2History.url("jdbc:h2:./owned;MODE=LEGACY;IFEXISTS=TRUE"));
    }

    @Test public void contradictoryModeIsNotSilentlyOverriddenOrEchoed() throws Exception {
        for (String url : new String[]{"jdbc:h2:mem:owned;MODE=REGULAR", "jdbc:h2:mem:owned;MODE=synthetic-secret",
                "jdbc:h2:mem:owned;MODE=LEGACY;MODE=REGULAR", "jdbc:h2:mem:owned;MODE=LEGACY;MODE=LEGACY"}) {
            try { ModernH2History.url(url); fail("Mode must be explicit and validated"); }
            catch (SQLException expected) {
                assertEquals("History H2 requires LEGACY compatibility mode", expected.getMessage());
                assertNull(expected.getCause());
            }
        }
    }

    @Test public void connectionSideEffectsAndIdentifierChangesCannotBypassPublicSchemaPreflight() throws Exception {
        for (String settings : new String[]{"INIT=synthetic-secret", "DATABASE_TO_LOWER=TRUE", "DATABASE_TO_UPPER=FALSE",
                "CASE_INSENSITIVE_IDENTIFIERS=TRUE", "SCHEMA=other", "SCHEMA_SEARCH_PATH=other,PUBLIC"}) {
            try { ModernH2History.url("jdbc:h2:mem:owned;" + settings); fail("Unvalidated schema settings must fail before connecting"); }
            catch (SQLException expected) { assertEquals("Unsupported history H2 initialization or identifier settings", expected.getMessage()); }
        }
    }
}
