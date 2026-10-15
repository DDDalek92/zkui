package com.deem.zkui.test;

import com.deem.zkui.utils.ServletUtil;
import com.deem.zkui.vo.LeafBean;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class NodeValueTest {
    @Test
    public void decodesUtf8WithoutTrimmingWhitespace() {
        String value = "  中文配置\nsecond line  ";
        LeafBean leaf = new LeafBean("/test", "key", value.getBytes(StandardCharsets.UTF_8));
        assertEquals(value, leaf.getStrValue());
    }

    @Test
    public void exportsLineBreaksWithoutCreatingExtraImportRecords() {
        assertEquals("first\\nsecond", ServletUtil.INSTANCE.externalizeNodeValue(
                "first\nsecond".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void exportsNullAsEmptyValue() {
        assertEquals("", ServletUtil.INSTANCE.externalizeNodeValue(null));
    }
}
