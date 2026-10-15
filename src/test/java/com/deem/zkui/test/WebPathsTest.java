package com.deem.zkui.test;
import com.deem.zkui.utils.WebPaths;
import org.junit.Test;
import static org.junit.Assert.*;
public class WebPathsTest {
    @Test public void onlyCanonicalLocalPrefixesAreAccepted() {
        assertEquals("/", WebPaths.context(null));
        assertEquals("/zkui", WebPaths.context("/zkui/"));
        assertEquals("/tools/zkui", WebPaths.context("/tools/zkui"));
        for (String path : new String[]{"", "//example.test", "https://example.test", "/a/../b", "/a/./b", "/a//b", "/a?x", "/%2f", "/a#x", "/a\r\nHeader:x"}) {
            try { WebPaths.context(path); fail("Invalid prefix accepted"); } catch (IllegalArgumentException expected) { }
        }
    }
}
