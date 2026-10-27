package com.shopvi.payment.infra;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC hex (chữ thường) và so sánh chữ ký thời gian hằng định (chống dò chữ ký theo thời gian phản hồi). */
final class Hmac {

    private Hmac() {
    }

    static String sha512(String key, String data) {
        return hex("HmacSHA512", key, data);
    }

    static String sha256(String key, String data) {
        return hex("HmacSHA256", key, data);
    }

    static boolean matches(String expectedHex, String actualHex) {
        if (actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                actualHex.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static String hex(String algorithm, String key, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
