package com.anirudh.storage.minis3.client;

import java.nio.charset.StandardCharsets;

/**
 * Percent-encodes URL path segments and query values. Everything except RFC 3986 unreserved characters is
 * encoded, so spaces become %20 (not '+') and non-ASCII keys are sent as UTF-8.
 *
 * @author Anirudh
 * @since 19/10/26
 */
final class PathEncoder {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private PathEncoder() {
    }

    static String encode(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (isUnreserved(c)) {
                out.append((char) c);
            } else {
                out.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
        return out.toString();
    }

    /**
     * Encodes each '/'-separated segment of an object key, keeping the slashes.
     */
    static String encodeKey(String key) {
        String[] segments = key.split("/", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(encode(segments[i]));
        }
        return out.toString();
    }

    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~';
    }
}
