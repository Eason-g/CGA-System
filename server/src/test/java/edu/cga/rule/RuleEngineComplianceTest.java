package edu.cga.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import edu.cga.domain.entity.User;
import org.junit.jupiter.api.Test;

/**
 * P1 第三批合规规则（与 ruleEngine.js 的 contractStartFrom / predictContractEnd / checkVisaCoverage 文案一致）。
 */
class RuleEngineComplianceTest {

    private static User intl(LocalDate expiry) {
        User u = new User();
        u.setId("S1");
        u.setIsInternational(true);
        u.setVisaValid(true);
        u.setVisaType("X1学习居留许可");
        u.setVisaExpiry(expiry);
        return u;
    }

    @Test
    void contractPeriodStartsNextMonday() {
        // 2026-11-20 是周五 → 下周一 11-23；周一当天也取下一个周一
        assertThat(RuleEngine.contractStartFrom(LocalDate.of(2026, 11, 20))).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(RuleEngine.contractStartFrom(LocalDate.of(2026, 11, 23))).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(RuleEngine.predictContractEnd(LocalDate.of(2026, 11, 20), 16)).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(RuleEngine.predictContractEnd(LocalDate.of(2026, 11, 20), 0)).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void visaCoverage() {
        LocalDate end = LocalDate.of(2027, 3, 15);
        assertThat(RuleEngine.checkVisaCoverage(intl(end), end)).isNull();
        assertThat(RuleEngine.checkVisaCoverage(intl(LocalDate.of(2027, 3, 14)), end))
                .isEqualTo("该国际生签证有效期至 2027-03-14，早于聘期结束日 2027-03-15，签证须覆盖整个聘期，不能录用（不可特批）");
        assertThat(RuleEngine.checkVisaCoverage(intl(null), end)).contains("未登记签证有效期");
    }

    @Test
    void visaExpiringSoonWithin30Days() {
        LocalDate today = LocalDate.of(2026, 11, 20);
        assertThat(RuleEngine.isVisaExpiringSoon(intl(today.plusDays(30)), today)).isTrue();
        assertThat(RuleEngine.isVisaExpiringSoon(intl(today.plusDays(31)), today)).isFalse();
        assertThat(RuleEngine.isVisaExpiringSoon(intl(today.minusDays(1)), today)).isTrue();
    }
}
