package edu.cga.crypto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 敏感数据迁移（P2 D.1），由 DatabaseInitializer 在建表/启动时调用，可重复执行（幂等）：
 * <ol>
 *   <li>老库补结构：users 增加 email_hash 列及索引，email/phone 列加宽到能放下密文，删除明文邮箱索引；</li>
 *   <li>把 email、phone 仍是明文的行加密（种子数据 data.sql 是明文，每次重建后都会走到这里）；</li>
 *   <li>按解密后的邮箱重算 email_hash，与库里不一致才更新（更换主密钥后会全部重算）。</li>
 * </ol>
 * 已是密文但解不开（密钥与加密时不一致）时抛异常，应用启动失败，避免用错误的密钥继续写入新数据。
 * 直接用 JDBC 读写原始列值，不经过 TypeHandler，才能区分“明文”和“密文”。
 */
@Component
public class SensitiveDataMigrator {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataMigrator.class);

    /** 加宽后的列长度：邮箱 128 字（含多字节字符）加密 + Base64 后不超过 512 */
    static final int EMAIL_COLUMN_LENGTH = 512;
    static final int PHONE_COLUMN_LENGTH = 255;

    private final JdbcTemplate jdbc;
    private final FieldCrypto crypto;

    public SensitiveDataMigrator(DataSource dataSource, FieldCrypto crypto) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.crypto = crypto;
    }

    /** @return 本次更新的行数 */
    public int migrate() {
        ensureSchema();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, email, phone, email_hash FROM users");
        Map<String, String> hashesByUser = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("id");
            String hash = crypto.emailHash(decryptContact(id, (String) row.get("email")));
            hashesByUser.put(id, hash);
            if (hash != null) {
                counts.merge(hash, 1, Integer::sum);
            }
        }
        int changed = 0;
        for (Map<String, Object> r : rows) {
            String id = (String) r.get("id");
            String email = (String) r.get("email");
            String phone = (String) r.get("phone");
            String oldHash = (String) r.get("email_hash");
            decryptContact(id, phone);
            String newEmail = needsEncrypt(email) ? crypto.encrypt(email) : email;
            String newPhone = needsEncrypt(phone) ? crypto.encrypt(phone) : phone;
            String hash = hashesByUser.get(id);
            String newHash = hash != null && counts.get(hash) == 1 ? hash : null;
            if (!Objects.equals(newEmail, email) || !Objects.equals(newPhone, phone) || !Objects.equals(newHash, oldHash)) {
                jdbc.update("UPDATE users SET email = ?, phone = ?, email_hash = ? WHERE id = ?",
                        newEmail, newPhone, newHash, id);
                changed++;
            }
        }
        ensureUniqueEmailIndex();
        if (changed > 0) {
            log.info("已加密 {} 个用户的手机号/邮箱并更新邮箱检索哈希", changed);
        }
        return changed;
    }

    private String decryptContact(String id, String encrypted) {
        try {
            return crypto.decrypt(encrypted);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("用户 " + id + " 的联系方式无法解密：请检查 app.crypto.key", e);
        }
    }

    private static boolean needsEncrypt(String v) {
        return v != null && !v.isEmpty() && !FieldCrypto.isEncrypted(v);
    }

    /** 老库（P0/P1 建的 users 表）补列、加宽、换索引；新库 schema.sql 已是最终结构，这里什么都不做 */
    private void ensureSchema() {
        if (!columnExists("email_hash")) {
            log.info("users 表缺少 email_hash 列（旧版本建库），自动补列");
            jdbc.execute("ALTER TABLE users ADD COLUMN email_hash CHAR(64) NULL COMMENT '小写邮箱的 HMAC-SHA256，用于邮箱登录' AFTER email");
        }
        if (indexExists("idx_users_email")) {
            // 随机 IV 的密文上建索引没有意义
            jdbc.execute("ALTER TABLE users DROP INDEX idx_users_email");
        }
        if (columnLength("email") < EMAIL_COLUMN_LENGTH) {
            jdbc.execute("ALTER TABLE users MODIFY email VARCHAR(" + EMAIL_COLUMN_LENGTH + ") NULL COMMENT 'AES-256-GCM 密文（enc:v1:）'");
        }
        if (columnLength("phone") < PHONE_COLUMN_LENGTH) {
            jdbc.execute("ALTER TABLE users MODIFY phone VARCHAR(" + PHONE_COLUMN_LENGTH + ") NULL COMMENT 'AES-256-GCM 密文（enc:v1:）'");
        }
    }

    private void ensureUniqueEmailIndex() {
        Integer nonUnique = jdbc.query("SELECT NON_UNIQUE FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'idx_users_email_hash'",
                rs -> rs.next() ? rs.getInt(1) : null);
        if (nonUnique == null) {
            jdbc.execute("ALTER TABLE users ADD UNIQUE KEY idx_users_email_hash (email_hash)");
        } else if (nonUnique != 0) {
            jdbc.execute("ALTER TABLE users DROP INDEX idx_users_email_hash, ADD UNIQUE KEY idx_users_email_hash (email_hash)");
        }
    }

    private boolean columnExists(String column) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = ?", Integer.class, column);
        return n != null && n > 0;
    }

    private int columnLength(String column) {
        Long len = jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = ?", Long.class, column);
        return len == null ? 0 : len.intValue();
    }

    private boolean indexExists(String index) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = ?", Integer.class, index);
        return n != null && n > 0;
    }
}
