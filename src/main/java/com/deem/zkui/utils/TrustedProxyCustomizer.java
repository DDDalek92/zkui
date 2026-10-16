package com.deem.zkui.utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;

/** Trust forwarding metadata only from explicitly named transport peers. */
public final class TrustedProxyCustomizer implements HttpConfiguration.Customizer {
    private final Set<InetAddress> peers;
    private final ForwardedRequestCustomizer delegate = new ForwardedRequestCustomizer();

    public TrustedProxyCustomizer(Properties props) {
        peers = parse(props.getProperty("trustedProxyAddresses", ""));
        if (peers.isEmpty()) throw new IllegalArgumentException("Forwarded headers require explicit trustedProxyAddresses IP literals");
    }

    private static Set<InetAddress> parse(String text) {
        Set<InetAddress> result = new HashSet<>();
        if (text.isBlank()) return result;
        try {
            for (String part : text.split(",", -1)) {
                String value = part.trim();
                if (value.contains(":")) {
                    if (!value.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException();
                } else {
                    if (!value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) throw new IllegalArgumentException();
                    for (String octet : value.split("\\.")) if (Integer.parseInt(octet) > 255 || (octet.length() > 1 && octet.startsWith("0"))) throw new IllegalArgumentException();
                }
                result.add(InetAddress.getByName(value)); // Only validated numeric literals; never resolve a hostname.
            }
        } catch (Exception failure) {
            throw new IllegalArgumentException("trustedProxyAddresses must contain comma-separated IPv4 or IPv6 literals");
        }
        return Set.copyOf(result);
    }

    @Override public Request customize(Request request, HttpFields.Mutable responseHeaders) {
        // Use the actual endpoint, before any forwarding wrapper can change metadata.
        var peer = request.getConnectionMetaData().getConnection().getEndPoint().getRemoteSocketAddress();
        if (peer instanceof InetSocketAddress address && peers.contains(address.getAddress())) {
            return delegate.customize(request, responseHeaders);
        }
        return request;
    }
}
