package com.deem.zkui.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Bounded UTF-8 input and operator-approved remote import sources. */
public final class ImportSafety {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_PARTS = 32;
    private ImportSafety() { }

    public static String read(InputStream input, int limit) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (bytes.size() + size > limit) throw new TooLargeException();
            if (System.nanoTime() > deadline) throw new IOException("Import read deadline exceeded");
            bytes.write(buffer, 0, size);
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
    }

    public static String fetch(String source, Properties props) throws IOException {
        if (!Boolean.parseBoolean(props.getProperty("remoteImportEnabled", "false"))) {
            throw new IOException("Remote URL import is disabled");
        }
        boolean approved = false;
        for (String entry : props.getProperty("remoteImportAllowedUrls", "").split("\\|")) {
            if (!entry.trim().isEmpty() && entry.trim().equals(source)) approved = true;
        }
        if (!approved) throw new IOException("Remote import URL is not approved");
        URL url = new URL(source);
        if ((!"https".equals(url.getProtocol()) && !"http".equals(url.getProtocol()))
                || url.getHost().isEmpty() || url.getUserInfo() != null || url.getRef() != null) {
            throw new IOException("Remote import requires an HTTP(S) URL without credentials or fragment");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Remote import requires HTTP 200; redirects are not followed");
            if (connection.getContentLengthLong() > MAX_BYTES) throw new TooLargeException();
            try (InputStream stream = connection.getInputStream()) {
                return read(stream, MAX_BYTES);
            }
        } finally {
            connection.disconnect();
        }
    }

    public static final class TooLargeException extends IOException {
        public TooLargeException() { super("Import exceeds the 1 MiB input limit"); }
    }
}
