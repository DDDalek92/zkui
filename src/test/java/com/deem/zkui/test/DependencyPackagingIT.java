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
            Properties version = new Properties();
            try (java.io.InputStream in = jar.getInputStream(jar.getEntry("META-INF/maven/ch.qos.reload4j/reload4j/pom.properties"))) {
                version.load(in);
            }
            assertEquals("1.2.27", version.getProperty("version"));
        }
    }
}
