package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import edu.cga.config.AppProperties;
import edu.cga.domain.entity.AuditLog;
import edu.cga.domain.entity.Notification;
import edu.cga.domain.entity.User;
import edu.cga.scope.Masker;
import edu.cga.scope.SqlCondition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 不依赖数据库的单元检查：实体 JSON 字段名、SqlCondition 组合、打码、配置绑定。
 */
class UnitChecksTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void userJsonUsesMockFieldNamesAndHidesPasswordHash() {
        User u = new User();
        u.setId("U_X");
        u.setPasswordHash("$2a$10$secret");
        u.setIsInternational(true);
        u.setVisaExpiry(LocalDate.parse("2027-07-31"));
        u.setLanguageLevel(new BigDecimal("7.0"));
        u.setSkills(List.of("Python"));
        JsonNode n = mapper.valueToTree(u);
        assertThat(n.has("passwordHash")).isFalse();
        assertThat(n.path("isInternational").asBoolean()).isTrue();
        assertThat(n.path("visaExpiry").asString()).isEqualTo("2027-07-31");
        assertThat(n.path("skills").isArray()).isTrue();
    }

    @Test
    void notificationAndAuditLogJsonFieldNames() {
        Notification notif = new Notification();
        notif.setRead(false);
        notif.setCreatedAt(LocalDateTime.parse("2026-11-20T08:00:00"));
        JsonNode n = mapper.valueToTree(notif);
        assertThat(n.has("read")).isTrue();
        assertThat(n.path("createdAt").asString()).isEqualTo("2026-11-20 08:00:00");

        AuditLog log = new AuditLog();
        log.setOperatorId("U_X");
        log.setTimestamp(LocalDateTime.parse("2026-10-12T09:30:00"));
        JsonNode l = mapper.valueToTree(log);
        assertThat(l.path("timestamp").asString()).isEqualTo("2026-10-12 09:30:00");
        assertThat(l.has("operatorId")).isFalse();
    }

    @Test
    void sqlConditionComposition() {
        SqlCondition jobs = SqlCondition.of("dept_id = ? OR mentor_id = ?", "DEPT_MATH", "U_M");
        SqlCondition sub = jobs.asInSubquery("target_id", "id", "jobs").and("target_type = 'JOB'");
        assertThat(sub.sql()).isEqualTo(
                "target_type = 'JOB' AND (target_id IN (SELECT id FROM jobs WHERE dept_id = ? OR mentor_id = ?))");
        assertThat(sub.args()).containsExactly("DEPT_MATH", "U_M");

        SqlCondition merged = SqlCondition.anyOf(SqlCondition.of("operator_id = ?", "U_M"), SqlCondition.NONE, sub);
        assertThat(merged.args()).containsExactly("U_M", "DEPT_MATH", "U_M");
        assertThat(merged.sql()).startsWith("(operator_id = ?) OR (target_type = 'JOB'");

        assertThat(SqlCondition.anyOf(SqlCondition.NONE, SqlCondition.NONE).isNone()).isTrue();
        assertThat(SqlCondition.anyOf(SqlCondition.ALL, jobs).isAll()).isTrue();
        assertThat(SqlCondition.NONE.asInSubquery("id", "job_id", "applications").isNone()).isTrue();
        assertThat(SqlCondition.ALL.asInSubquery("id", "mentor_id", "jobs").sql())
                .isEqualTo("id IN (SELECT mentor_id FROM jobs)");
    }

    @Test
    void masking() {
        assertThat(Masker.phone("138-0010-2091")).isEqualTo("138****2091");
        assertThat(Masker.email("alex.chen@univ.edu.cn")).isEqualTo("a***@univ.edu.cn");
        assertThat(Masker.phone(null)).isNull();
    }

    @Test
    void appPropertiesBinding() {
        var source = new MapConfigurationPropertySource(Map.of(
                "app.today", "2026-11-20",
                "app.semester-start", "2026-08-31",
                "app.jwt.ttl", "8h",
                "app.db.init", "if-empty",
                "app.cors.allowed-origins[0]", "http://localhost:5173"));
        AppProperties p = new Binder(source).bind("app", AppProperties.class).get();
        assertThat(p.getToday()).isEqualTo(LocalDate.parse("2026-11-20"));
        assertThat(p.getSemesterStart()).isEqualTo(LocalDate.parse("2026-08-31"));
        assertThat(p.getJwt().getTtl()).isEqualTo(Duration.ofHours(8));
        assertThat(p.getDb().getInit()).isEqualTo(AppProperties.InitMode.IF_EMPTY);
        assertThat(p.getCors().getAllowedOrigins()).containsExactly("http://localhost:5173");
    }
}
