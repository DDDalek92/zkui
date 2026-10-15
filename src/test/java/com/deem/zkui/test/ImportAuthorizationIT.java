package com.deem.zkui.test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImportAuthorizationIT {
    @ClassRule public static TestApplication app = new TestApplication("import-authorization");

    @Test public void readonlyUserCannotCreateOverwriteOrDelete() throws Exception {
        app.create("/appconfig/readonly-existing", "original");
        app.create("/appconfig/readonly-delete", "keep");
        TestApplication.Reply reply = app.upload("/appconfig=readonly-new=denied\n"
                + "/appconfig=readonly-existing=denied\n-/appconfig/readonly-delete", app.login("test-user"));
        assertNull("Readonly import must not create nodes", app.observer.exists("/appconfig/readonly-new", false));
        assertEquals("original", app.read("/appconfig/readonly-existing"));
        assertEquals("keep", app.read("/appconfig/readonly-delete"));
        assertEquals(403, reply.status);
    }

    @Test public void anonymousImportDoesNotWrite() throws Exception {
        TestApplication.Reply reply = app.upload("/appconfig=anonymous-new=denied", null);
        assertEquals(302, reply.status);
        assertNull(app.observer.exists("/appconfig/anonymous-new", false));
    }

    @Test public void unknownRoleDoesNotWrite() throws Exception {
        TestApplication.Reply reply = app.upload("/appconfig=unknown-role-new=denied", app.login("test-other"));
        assertNull(app.observer.exists("/appconfig/unknown-role-new", false));
        assertEquals(403, reply.status);
    }

    @Test public void deniesReadonlyBeforeParsingMalformedUpload() throws Exception {
        assertEquals(403, app.multipart("invalid multipart", app.login("test-user")).status);
    }

    @Test public void deniesReadonlyBeforeFetchingRemoteImport() throws Exception {
        try (ServerSocket source = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            String body = "--zkui-test\r\nContent-Disposition: form-data; name=\"scmServer\"\r\n\r\n"
                    + "http://127.0.0.1:" + source.getLocalPort() + "/\r\n--zkui-test--\r\n";
            assertEquals(403, app.multipart(body, app.login("test-user")).status);
            source.setSoTimeout(200);
            assertThrows("Unauthorized request must not open a URL connection", SocketTimeoutException.class, source::accept);
        }
    }

    @Test public void adminCanCreateOverwriteAndDelete() throws Exception {
        app.create("/appconfig/admin-existing", "original");
        app.create("/appconfig/admin-delete", "remove");
        TestApplication.Reply reply = app.upload("/appconfig=admin-new=created\n"
                + "/appconfig=admin-existing=updated\n-/appconfig/admin-delete", app.login("test-admin"));
        assertEquals(302, reply.status);
        assertEquals("created", app.read("/appconfig/admin-new"));
        assertEquals("updated", app.read("/appconfig/admin-existing"));
        assertNull(app.observer.exists("/appconfig/admin-delete", false));
    }
}
