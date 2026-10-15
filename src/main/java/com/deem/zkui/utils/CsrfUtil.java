package com.deem.zkui.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.http.HttpSession;

/** Session-bound synchronizer tokens, independent of session identifiers. */
public final class CsrfUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ATTRIBUTE = "csrfToken";

    private CsrfUtil() { }

    public static String token(HttpSession session) {
        synchronized (session) {
            String token = (String) session.getAttribute(ATTRIBUTE);
            return token == null ? rotate(session) : token;
        }
    }

    public static String rotate(HttpSession session) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(ATTRIBUTE, token);
        return token;
    }

    public static boolean valid(HttpSession session, String submitted) {
        if (session == null || submitted == null) return false;
        String expected = (String) session.getAttribute(ATTRIBUTE);
        return expected != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                submitted.getBytes(StandardCharsets.UTF_8));
    }
}
