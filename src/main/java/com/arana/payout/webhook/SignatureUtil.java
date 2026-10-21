package com.arana.payout.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 webhook signatures, in the style Stripe/Payoneer use.
 *
 * The signed value is "<timestamp>.<rawBody>" so the signature also pins the
 * timestamp (an attacker can't replay an old body under a new time). The header
 * looks like:  X-Signature: t=1760712000,v1=5257a869e7b...
 */
public final class SignatureUtil {

    private SignatureUtil() {}

    public static String sign(long timestamp, String rawBody, String secret) {
        String signedPayload = timestamp + "." + rawBody;
        byte[] mac = hmacSha256(signedPayload, secret);
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac);
    }

    /** Recomputes the signature and compares it in constant time. */
    public static boolean verify(String header, String rawBody, String secret, long nowEpoch, long toleranceSeconds) {
        if (header == null) {
            return false;
        }
        long t = 0;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            switch (kv[0].trim()) {
                case "t" -> t = parseLongOrZero(kv[1].trim());
                case "v1" -> v1 = kv[1].trim();
            }
        }
        if (v1 == null || t == 0) {
            return false;
        }
        // Reject stale signatures (replay protection).
        if (Math.abs(nowEpoch - t) > toleranceSeconds) {
            return false;
        }
        byte[] expected = hmacSha256(t + "." + rawBody, secret);
        byte[] provided = HexFormat.of().parseHex(v1);
        return MessageDigest.isEqual(expected, provided);
    }

    private static byte[] hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("could not compute HMAC", e);
        }
    }

    private static long parseLongOrZero(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
