package edu.cga.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感字段加密配置（P2 D.1）：启动时校验 app.crypto.key，非法则上下文创建失败、应用无法启动，
 * 不会出现“带着无效密钥对外服务、写进库里的数据以后解不开”的情况。
 */
@Configuration
public class CryptoConfig {

    @Bean
    public FieldCrypto fieldCrypto(@Value("${app.crypto.key:}") String key) {
        FieldCrypto crypto = FieldCrypto.fromBase64(key);
        FieldCrypto.install(crypto);
        return crypto;
    }
}
