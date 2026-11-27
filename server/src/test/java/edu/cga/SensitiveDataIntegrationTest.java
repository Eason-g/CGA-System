package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import edu.cga.crypto.FieldCrypto;
import edu.cga.crypto.SensitiveDataMigrator;
import edu.cga.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * P2 D.1 敏感信息加密：直接用 SQL 查库确认手机号/邮箱是密文；快照本人明文、他人打码；
 * 邮箱登录走 email_hash；改邮箱后新邮箱能登录、旧邮箱不能；启动迁移幂等并能升级老库；报表和审计日志不含明文。
 */
class SensitiveDataIntegrationTest extends WriteApiTestBase {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private SensitiveDataMigrator migrator;
    @Autowired
    private FieldCrypto crypto;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private int loginStatus(String account) throws Exception {
        String body = json.writeValueAsString(Map.of("account", account, "password", DEMO_PASSWORD));
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void phoneAndEmailAreCiphertextInDatabase() {
        List<Map<String, Object>> rows = jdbc().queryForList("SELECT id, email, phone, email_hash FROM users");
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> r : rows) {
            String email = (String) r.get("email");
            String phone = (String) r.get("phone");
            if (email != null && !email.isEmpty()) {
                assertThat(email).as("用户 %s 的邮箱", r.get("id")).startsWith(FieldCrypto.PREFIX).doesNotContain("@");
                assertThat((String) r.get("email_hash")).matches("[0-9a-f]{64}");
            }
            if (phone != null && !phone.isEmpty()) {
                assertThat(phone).as("用户 %s 的手机号", r.get("id")).startsWith(FieldCrypto.PREFIX);
            }
        }
        // 种子明文在库里任何列都查不到
        Integer plain = jdbc().queryForObject(
                "SELECT COUNT(*) FROM users WHERE email LIKE '%@univ.edu.cn' OR phone = '138-0010-2091'", Integer.class);
        assertThat(plain).isZero();
        // 通过实体读取时透明解密
        User alex = userMapper.selectById("U_INTL_STU");
        assertThat(alex.getPhone()).isEqualTo("138-0010-2091");
        assertThat(alex.getEmail()).isEqualTo("alex.chen@univ.edu.cn");
        // 随机 IV：同一明文重新加密结果不同
        String stored = jdbc().queryForObject("SELECT phone FROM users WHERE id = 'U_INTL_STU'", String.class);
        assertThat(crypto.encrypt("138-0010-2091")).isNotEqualTo(stored);
    }

    @Test
    void snapshotShowsOwnPlaintextAndMasksOthers() throws Exception {
        JsonNode own = snapshotAs("alex.chen").path("users");
        JsonNode alex = find(own, "U_INTL_STU");
        assertThat(alex.path("phone").asString()).isEqualTo("138-0010-2091");
        assertThat(alex.path("email").asString()).isEqualTo("alex.chen@univ.edu.cn");
        assertThat(alex.has("emailHash")).isFalse();

        JsonNode mentorView = snapshotAs("zhang.mingyuan").path("users");
        JsonNode alexSeenByMentor = find(mentorView, "U_INTL_STU");
        assertThat(alexSeenByMentor.path("phone").asString()).isEqualTo("138****2091");
        assertThat(alexSeenByMentor.path("email").asString()).isEqualTo("a***@univ.edu.cn");
        assertThat(mentorView.toString()).doesNotContain(FieldCrypto.PREFIX).doesNotContain("emailHash");
    }

    @Test
    void loginByEmailUsesHashIgnoringCase() throws Exception {
        assertThat(loginStatus("Alex.Chen@UNIV.edu.cn")).isEqualTo(200);
        assertThat(loginStatus("  su.yt@univ.edu.cn ")).isEqualTo(200);
        assertThat(loginStatus("nobody@univ.edu.cn")).isEqualTo(401);
    }

    @Test
    void changingEmailUpdatesHashSoNewEmailLogsInAndOldDoesNot() throws Exception {
        String oldEmail = userMapper.selectById("U_STU_ELENA").getEmail();
        assertThat(loginStatus(oldEmail)).isEqualTo(200);
        String oldHash = jdbc().queryForObject("SELECT email_hash FROM users WHERE id = 'U_STU_ELENA'", String.class);

        String newEmail = "Elena.New@univ.edu.cn";
        JsonNode resp = apiPut("/api/users/me", "elena.rossi", Map.of("email", newEmail, "phone", "137-0000-9999"), 200);
        assertThat(resp.path("data").path("email").asString()).isEqualTo(newEmail);

        Map<String, Object> row = jdbc().queryForMap("SELECT email, phone, email_hash FROM users WHERE id = 'U_STU_ELENA'");
        assertThat((String) row.get("email")).startsWith(FieldCrypto.PREFIX);
        assertThat((String) row.get("phone")).startsWith(FieldCrypto.PREFIX);
        assertThat((String) row.get("email_hash")).isNotEqualTo(oldHash).isEqualTo(crypto.emailHash("elena.new@univ.edu.cn"));

        assertThat(loginStatus("elena.new@univ.edu.cn")).isEqualTo(200);
        assertThat(loginStatus(oldEmail)).isEqualTo(401);

        // 审计日志里没有新旧邮箱、手机号明文
        String all = "CONCAT_WS('|', action, opinion, before_value, after_value)";
        Integer leaked = jdbc().queryForObject("SELECT COUNT(*) FROM audit_log WHERE " + all + " LIKE ? OR "
                + all + " LIKE ? OR " + all + " LIKE ?",
                Integer.class, "%univ.edu.cn%", "%137-0000-9999%", "%elena.new%");
        assertThat(leaked).isZero();

        // 清空邮箱：检索哈希同步置空
        apiPut("/api/users/me", "elena.rossi", Map.of("email", ""), 200);
        assertThat(jdbc().queryForObject("SELECT email_hash FROM users WHERE id = 'U_STU_ELENA'", String.class)).isNull();
        assertThat(loginStatus("elena.new@univ.edu.cn")).isEqualTo(401);
    }

    @Test
    void profileRejectsAnotherAccountsNormalizedEmail() throws Exception {
        Object before = jdbc().queryForObject("SELECT email FROM users WHERE id = 'U_STU_ELENA'", String.class);
        apiPut("/api/users/me", "elena.rossi", Map.of("email", "LIN.ZHIYUAN@univ.edu.cn"), 409);
        assertThat(jdbc().queryForObject("SELECT email FROM users WHERE id = 'U_STU_ELENA'", String.class))
                .isEqualTo(before);
    }

    @Test
    void migrationIsIdempotentAndUpgradesOldSchema() throws Exception {
        assertThat(migrator.migrate()).isZero();

        // 模拟 P1 老库：没有 email_hash 列、邮箱是明文并带明文索引
        jdbc().execute("ALTER TABLE users DROP INDEX idx_users_email_hash");
        jdbc().execute("ALTER TABLE users DROP COLUMN email_hash");
        jdbc().execute("ALTER TABLE users ADD KEY idx_users_email (email)");
        jdbc().update("UPDATE users SET email = 'lin.zhiyuan@univ.edu.cn', phone = '139-1024-8812' WHERE id = 'U_CN_STU'");

        int changed = migrator.migrate();
        assertThat(changed).isPositive(); // 所有有邮箱的行都要补哈希
        Map<String, Object> lin = jdbc().queryForMap("SELECT email, phone, email_hash FROM users WHERE id = 'U_CN_STU'");
        assertThat((String) lin.get("email")).startsWith(FieldCrypto.PREFIX);
        assertThat((String) lin.get("phone")).startsWith(FieldCrypto.PREFIX);
        assertThat((String) lin.get("email_hash")).isEqualTo(crypto.emailHash("lin.zhiyuan@univ.edu.cn"));
        Integer oldIndex = jdbc().queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name = 'users' AND index_name = 'idx_users_email'", Integer.class);
        assertThat(oldIndex).isZero();
        assertThat(migrator.migrate()).isZero();
        assertThat(loginStatus("lin.zhiyuan@univ.edu.cn")).isEqualTo(200);
        assertThat(userMapper.selectById("U_CN_STU").getPhone()).isEqualTo("139-1024-8812");
    }

    @Test
    void plaintextRowsAreStillReadableBeforeMigration() {
        jdbc().update("UPDATE users SET phone = '135-1111-2222' WHERE id = 'U_STU_SU'");
        assertThat(userMapper.selectById("U_STU_SU").getPhone()).isEqualTo("135-1111-2222");
        migrator.migrate();
        assertThat(jdbc().queryForObject("SELECT phone FROM users WHERE id = 'U_STU_SU'", String.class))
                .startsWith(FieldCrypto.PREFIX);
        assertThat(userMapper.selectById("U_STU_SU").getPhone()).isEqualTo("135-1111-2222");
    }

    @Test
    void reportExportContainsNoContactInfo() throws Exception {
        String token = tokenOf("zhou.guoqiang");
        for (String type : List.of("jobs", "hires", "timesheets", "payrolls", "budgets")) {
            String csv = new String(mvc.perform(get("/api/reports/" + type + "/export?format=csv")
                            .header("Authorization", bearer(token)))
                    .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            assertThat(csv).as(type).doesNotContain("@univ.edu.cn").doesNotContain(FieldCrypto.PREFIX)
                    .doesNotContain("138-0010-2091").doesNotContain("138****2091");
        }
    }

    private static JsonNode find(JsonNode users, String id) {
        for (JsonNode u : users) {
            if (id.equals(u.path("id").asString())) {
                return u;
            }
        }
        throw new AssertionError("快照中没有用户 " + id);
    }
}
