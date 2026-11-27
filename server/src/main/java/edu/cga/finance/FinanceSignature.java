package edu.cga.finance;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 财务回调签名：HMAC-SHA256(密钥, 原始请求体)，十六进制小写，放在请求头 X-Finance-Signature。
 */
public final class FinanceSignature {

    /** 签名请求头 */
    public static final String HEADER = "X-Finance-Signature";

    private FinanceSignature() {
    }

    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    /** 常量时间比较，避免按响应时间猜签名 */
    public static boolean verify(String secret, String body, String signature) {
        if (secret == null || secret.isEmpty() || body == null || signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
