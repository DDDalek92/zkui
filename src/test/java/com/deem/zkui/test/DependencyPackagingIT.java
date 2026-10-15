package com.deem.zkui.test;
import java.util.Properties;
import java.util.jar.JarFile;
import org.junit.Test;
import static org.junit.Assert.*;

public class DependencyPackagingIT {
    @Test public void assembledJarContainsOnlyTheSelectedLoggingBindingAndReplacement() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("zkui.test.jar"))) {
            assertNull(jar.getEntry("META-INF/maven/log4j/log4j/pom.properties"));
            assertNull(jar.getEntry("META-INF/maven/org.slf4j/slf4j-log4j12/pom.properties"));
            assertNotNull(jar.getEntry("META-INF/maven/org.slf4j/slf4j-reload4j/pom.properties"));
            assertNull(jar.getEntry("org/slf4j/impl/StaticLoggerBinder.class"));
            try (java.io.InputStream in = jar.getInputStream(jar.getEntry("META-INF/services/org.slf4j.spi.SLF4JServiceProvider"))) {
                assertEquals("org.slf4j.reload4j.Reload4jServiceProvider",
                        new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim());
            }
            assertEquals("2.0.20", version(jar, "org.slf4j/slf4j-api"));
            assertEquals("2.0.20", version(jar, "org.slf4j/slf4j-reload4j"));
            Properties version = new Properties();
            try (java.io.InputStream in = jar.getInputStream(jar.getEntry("META-INF/maven/ch.qos.reload4j/reload4j/pom.properties"))) {
                version.load(in);
            }
            assertEquals("1.2.27", version.getProperty("version"));
        }
    }

    private String version(JarFile jar, String coordinates) throws Exception {
        Properties properties = new Properties();
        try (java.io.InputStream in = jar.getInputStream(jar.getEntry("META-INF/maven/" + coordinates + "/pom.properties"))) {
            properties.load(in);
        }
        return properties.getProperty("version");
    }

    @Test public void java17JarHasOneEe8ApiAlignedJettyAndNoLegacyOrm() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("zkui.test.jar"))) {
            assertNull(jar.getEntry("org/javalite/activejdbc/Model.class"));
            assertEquals("13.10.3", version(jar, "org.flywaydb/flyway-core"));
            // MySQL's modern module is a copied, isolated rehearsal artifact, not runtime yet.
            assertNull(jar.getEntry("org/flywaydb/database/mysql/MySQLDatabase.class"));
            // Legacy Flyway remains only for the separately validated MySQL path in this increment.
            assertEquals("2.2.1", version(jar, "com.googlecode.flyway/flyway-core"));
            try (java.net.URLClassLoader isolated = new java.net.URLClassLoader(
                    new java.net.URL[]{new java.io.File(jar.getName()).toURI().toURL()}, ClassLoader.getPlatformClassLoader())) {
                Class<?> constants = isolated.loadClass("org.h2.engine.Constants");
                assertEquals(2, constants.getField("VERSION_MAJOR").get(null));
                assertEquals(5, constants.getField("VERSION_MINOR").get(null));
                assertEquals(252, constants.getField("BUILD_ID").get(null));
            }
            assertNull(jar.getEntry("org/eclipse/jetty/webapp/WebAppContext.class"));
            assertNull(jar.getEntry("META-INF/maven/javax.servlet/javax.servlet-api/pom.properties"));
            assertNotNull(jar.getEntry("javax/servlet/http/HttpServlet.class"));
            assertNull(jar.getEntry("jakarta/servlet/http/HttpServlet.class"));
            assertEquals("4.0.9", version(jar, "org.eclipse.jetty.toolchain/jetty-servlet-api"));
            int modules = 0;
            for (java.util.jar.JarEntry entry : java.util.Collections.list(jar.entries())) {
                String name = entry.getName();
                if (name.startsWith("META-INF/maven/org.eclipse.jetty")
                        && !name.startsWith("META-INF/maven/org.eclipse.jetty.toolchain/")
                        && name.endsWith("/pom.properties")) {
                    Properties properties = new Properties();
                    try (java.io.InputStream in = jar.getInputStream(entry)) { properties.load(in); }
                    assertEquals(name, "12.1.14", properties.getProperty("version"));
                    modules++;
                }
            }
            assertTrue("Core and EE8 modules must be packaged", modules >= 8);
            try (java.io.DataInputStream in = new java.io.DataInputStream(jar.getInputStream(
                    jar.getEntry("com/deem/zkui/domain/History.class")))) {
                assertEquals(0xCAFEBABE, in.readInt());
                in.readUnsignedShort();
                assertEquals(61, in.readUnsignedShort());
            }
        }
    }
}
