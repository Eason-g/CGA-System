package edu.cga.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * P2 D.1 字段加密单元测试（不连数据库）：加解密、随机 IV、明文兼容、篡改检测、邮箱哈希、密钥校验与启动失败。
 */
class FieldCryptoTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final FieldCrypto crypto = FieldCrypto.fromBase64(KEY);

    @Test
    void roundTripWithRandomIv() {
        String a = crypto.encrypt("138-0010-2091");
        String b = crypto.encrypt("138-0010-2091");
        assertThat(a).startsWith("enc:v1:").isNotEqualTo(b).doesNotContain("2091");
        assertThat(crypto.decrypt(a)).isEqualTo("138-0010-2091");
        assertThat(crypto.decrypt(b)).isEqualTo("138-0010-2091");
        assertThat(crypto.decrypt(crypto.encrypt("中文 邮箱@例子.cn"))).isEqualTo("中文 邮箱@例子.cn");
    }

    @Test
    void plaintextAndNullPassThrough() {
        assertThat(crypto.decrypt("alex.chen@univ.edu.cn")).isEqualTo("alex.chen@univ.edu.cn");
        assertThat(crypto.decrypt(null)).isNull();
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.encrypt("")).isEmpty();
    }

    @Test
    void tamperedCiphertextOrWrongKeyFails() {
        String c = crypto.encrypt("secret@univ.edu.cn");
        byte[] raw = Base64.getDecoder().decode(c.substring(FieldCrypto.PREFIX.length()));
        raw[raw.length - 1] ^= 1;
        String tampered = FieldCrypto.PREFIX + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class).hasMessageContaining("解密失败");
        assertThatThrownBy(() -> FieldCrypto.fromBase64(OTHER_KEY).decrypt(c)).hasMessageContaining("密钥不一致");
        assertThatThrownBy(() -> crypto.decrypt("enc:v1:@@@")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emailHashIsKeyedAndCaseInsensitive() throws Exception {
        String h = crypto.emailHash("Alex.Chen@UNIV.edu.cn ");
        assertThat(h).matches("[0-9a-f]{64}").isEqualTo(crypto.emailHash("alex.chen@univ.edu.cn"));
        assertThat(h).isNotEqualTo(FieldCrypto.fromBase64(OTHER_KEY).emailHash("alex.chen@univ.edu.cn"));
        String plainSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("alex.chen@univ.edu.cn".getBytes(StandardCharsets.UTF_8)));
        assertThat(h).isNotEqualTo(plainSha);
        assertThat(crypto.emailHash("  ")).isNull();
    }

    @Test
    void hkdfMatchesRfc5869TestCase1() {
        byte[] ikm = HexFormat.of().parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = HexFormat.of().parseHex("000102030405060708090a0b0c");
        byte[] info = HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9");
        // RFC 5869 A.1 的 OKM 前 32 字节
        assertThat(HexFormat.of().formatHex(FieldCrypto.hkdfSha256(ikm, salt, info)))
                .isEqualTo("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf");
    }

    @Test
    void invalidKeysAreRejectedWithClearMessage() {
        assertThatThrownBy(() -> FieldCrypto.fromBase64(null)).hasMessageContaining("缺少配置 app.crypto.key");
        assertThatThrownBy(() -> FieldCrypto.fromBase64(" ")).hasMessageContaining("缺少配置 app.crypto.key");
        assertThatThrownBy(() -> FieldCrypto.fromBase64("不是base64!!")).hasMessageContaining("不是合法的 Base64");
        assertThatThrownBy(() -> FieldCrypto.fromBase64(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("应为 32 字节").hasMessageContaining("实际 16 字节");
    }

    @Test
    void contextFailsToStartWithMissingOrShortKey() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(CryptoConfig.class);
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("缺少配置 app.crypto.key");
        });
        runner.withPropertyValues("app.crypto.key=" + Base64.getEncoder().encodeToString(new byte[24]))
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("应为 32 字节");
                });
    }
}
