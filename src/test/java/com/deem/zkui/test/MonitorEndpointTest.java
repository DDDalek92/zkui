package com.deem.zkui.test;

import org.apache.zookeeper.client.ConnectStringParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class MonitorEndpointTest {
    @Test public void nativeParserSeparatesEnsembleIpv6AndChroot() {
        ConnectStringParser parser = new ConnectStringParser("127.0.0.1:2181,[::1]:2182/parent/child");
        assertEquals("/parent/child", parser.getChrootPath());
        assertEquals(2, parser.getServerAddresses().size());
        assertEquals(2182, parser.getServerAddresses().get(1).getPort());
        assertTrue(parser.getServerAddresses().get(1).getHostString().contains("::1"));
    }
}
