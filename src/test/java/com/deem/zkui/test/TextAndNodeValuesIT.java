package com.deem.zkui.test;

import java.net.URLEncoder;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class TextAndNodeValuesIT {
    @ClassRule public static TestApplication app = new TestApplication("text-node-values", "testFileEncoding", "US-ASCII");

    @Test public void browserFormUsesUtf8WithoutACharsetAndPreservesWhitespace() throws Exception {
        String cookie = app.login("test-admin");
        String value = "  中文配置\n  second line  ";
        String form = "action=Save+Property&currentPath=%2Fappconfig%2F&displayPath=%2Fappconfig"
                + "&newProperty=" + URLEncoder.encode("测试名字", "UTF-8")
                + "&newValue=" + URLEncoder.encode(value, "UTF-8");
        assertEquals(302, app.requestWithToken("/home", form, cookie, "application/x-www-form-urlencoded",
                TestApplication.token(app.get("/home", cookie).body)).status);
        assertEquals(value, app.read("/appconfig/测试名字"));
        assertTrue(app.get("/export?zkPath=%2Fappconfig", cookie).body.contains("测试名字=  中文配置\\n  second line  "));
    }

    @Test public void uploadedChineseTextSurvivesNonUtf8JvmDefault() throws Exception {
        assertEquals(302, app.upload("/=导入名字=测试值hahaha", app.login("test-admin")).status);
        assertEquals("测试值hahaha", app.read("/导入名字"));
    }

    @Test public void displaysEscapedValueOfANodeThatAlsoHasChildren() throws Exception {
        app.create("/appconfig/parent-value", "  parent 中文\n<script>parent-marker</script>  ");
        app.create("/appconfig/parent-value/child", "child");
        String html = app.get("/home?zkPath=%2Fappconfig%2Fparent-value", app.login("test-admin")).body;
        assertTrue(html.contains("Current node value"));
        assertTrue(html.contains("  parent 中文\n&lt;script&gt;parent-marker&lt;/script&gt;  "));
        assertFalse(html.contains("<script>parent-marker</script>"));
    }

    @Test public void currentNodeValueHonorsPasswordMasking() throws Exception {
        app.create("/appconfig/db.Password", "synthetic-parent-secret");
        app.create("/appconfig/db.Password/child", "child");
        String html = app.get("/home?zkPath=%2Fappconfig%2Fdb.Password", app.login("test-user")).body;
        assertTrue(html.contains("SOPA/PIPA BLACKLISTED VALUE"));
        assertFalse(html.contains("synthetic-parent-secret"));
    }
}
