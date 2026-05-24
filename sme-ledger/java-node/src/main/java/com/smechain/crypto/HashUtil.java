package com.smechain.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HashUtil {
    private HashUtil(){}

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String sha256Hex(String s) {
        return toHex(sha256(s.getBytes(StandardCharsets.UTF_8)));
    }

    public static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
