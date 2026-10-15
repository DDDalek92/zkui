package com.deem.zkui.test;

import com.deem.zkui.utils.ImportSafety;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImportSafetyTest {
    @Test public void boundsAndDecodesUtf8WithoutTrimming() throws Exception {
        String value = "  中文\nsecond  ";
        assertEquals(value, ImportSafety.read(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)), 100));
        assertThrows(ImportSafety.TooLargeException.class, () -> ImportSafety.read(new ByteArrayInputStream(new byte[101]), 100));
        assertThrows(IOException.class, () -> ImportSafety.read(new ByteArrayInputStream(new byte[]{(byte) 0xff}), 100));
    }

    @Test public void disabledOrUnapprovedSourcesAreRejectedBeforeAccess() {
        Properties props = new Properties();
        assertThrows(IOException.class, () -> ImportSafety.fetch("http://127.0.0.1:1/", props));
        props.setProperty("remoteImportEnabled", "true");
        assertThrows(IOException.class, () -> ImportSafety.fetch("http://127.0.0.1:1/", props));
        props.setProperty("remoteImportAllowedUrls", "file:///etc/passwd");
        assertThrows(IOException.class, () -> ImportSafety.fetch("file:///etc/passwd", props));
    }
}
