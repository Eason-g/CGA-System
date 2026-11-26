package edu.cga.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 敏感字段加密（P2 D.1）：AES-256-GCM 加密手机号、邮箱，HMAC-SHA256 计算邮箱检索哈希。
 * <ul>
 *   <li>密文格式 {@code enc:v1:<Base64(IV 12 字节 | 密文 | GCM 标签 16 字节)>}，每次加密随机生成 IV，
 *       同一明文两次加密结果不同，库里看不出哪些用户手机号相同；</li>
 *   <li>读取时不带 {@code enc:v1:} 前缀的值按明文原样返回，兼容尚未迁移的旧数据；</li>
 *   <li>检索哈希的密钥用 HKDF-SHA256 从 {@code app.crypto.key} 派生（info = {@value #HASH_KEY_INFO}），
 *       不直接复用加密密钥：同一把密钥不同时用于两种算法，派生出的两把密钥在密码学上相互独立；
 *       又只需要管理一个配置项（只配一个环境变量，不会出现只换了其中一把的情况）。
 *       代价是更换主密钥时检索哈希也要重算，启动迁移会按新密钥重算全部 email_hash。</li>
 * </ul>
 * MyBatis 自己实例化 TypeHandler，拿不到 Spring Bean，所以由 {@link CryptoConfig} 在启动时
 * 校验密钥并通过 {@link #install(FieldCrypto)} 注册为全局实例。
 */
public final class FieldCrypto {

    public static final String PREFIX = "enc:v1:";
    static final String HASH_KEY_INFO = "cga-email-hash-v1";

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile FieldCrypto installed;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hashKey;

    private FieldCrypto(byte[] key) {
        this.aesKey = new SecretKeySpec(key, "AES");
        this.hashKey = new SecretKeySpec(hkdfSha256(key, "cga-system".getBytes(StandardCharsets.UTF_8),
                HASH_KEY_INFO.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
    }

    /**
     * 按配置值创建；密钥缺失、不是 Base64 或解码后不是 32 字节时抛出 {@link IllegalStateException}，
     * 消息写明原因，应用因此启动失败。
     */
    public static FieldCrypto fromBase64(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("缺少配置 app.crypto.key：需要 Base64 编码的 32 字节 AES-256 密钥，"
                    + "生产环境请通过环境变量 APP_CRYPTO_KEY 设置（可用 openssl rand -base64 32 生成）");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("配置 app.crypto.key 不是合法的 Base64 字符串", e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException("配置 app.crypto.key 长度不正确：Base64 解码后应为 32 字节（AES-256），实际 "
                    + key.length + " 字节");
        }
        return new FieldCrypto(key);
    }

    /** 注册全局实例，供 {@link EncryptedStringTypeHandler} 使用 */
    public static void install(FieldCrypto crypto) {
        installed = crypto;
    }

    public static FieldCrypto get() {
        FieldCrypto c = installed;
        if (c == null) {
            throw new IllegalStateException("字段加密尚未初始化（CryptoConfig 未加载或 app.crypto.key 无效）");
        }
        return c;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 加密；null 原样返回，空串也原样保存（空值本身不含信息，且便于区分“未填写”） */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    /** 解密；不带前缀的值视为历史明文原样返回。密文被篡改或密钥不一致时抛出 {@link IllegalStateException} */
    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (all.length < IV_BYTES + TAG_BITS / 8) {
                throw new IllegalStateException("敏感字段密文长度不正确");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("敏感字段解密失败：密文被篡改，或 app.crypto.key 与加密时使用的密钥不一致", e);
        }
    }

    /**
     * 邮箱检索哈希：HMAC-SHA256(去首尾空格并转小写的邮箱)，64 位十六进制；空值返回 null。
     * 用带密钥的 HMAC 而不是普通 SHA-256：邮箱取值空间小，无密钥的哈希可以被字典枚举还原。
     */
    public String emailHash(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            byte[] h = mac.doFinal(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("邮箱哈希计算失败", e);
        }
    }

    /** RFC 5869 HKDF-SHA256，输出 32 字节（只需要一个分组） */
    static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(info);
            mac.update((byte) 1);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HKDF 密钥派生失败", e);
        }
    }
}
