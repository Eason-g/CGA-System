package edu.cga.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import edu.cga.domain.entity.Job;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.User;
import org.junit.jupiter.api.Test;

/** P2 B.2 学籍状态规则（文案与 ruleEngine.js 的 checkEnrollment / validateHire 一致） */
class RuleEngineEnrollmentTest {

    private static User student(String status) {
        User u = new User();
        u.setId("S1");
        u.setRole("STUDENT");
        u.setIsInternational(false);
        u.setEnrollmentStatus(status);
        return u;
    }

    private static Job job() {
        Job j = new Job();
        j.setId("J1");
        j.setQuota(2);
        j.setHiredCount(0);
        j.setWeeklyHours(4);
        j.setWeeks(8);
        return j;
    }

    @Test
    void enrolledOrUnknownPasses() {
        assertThat(RuleEngine.checkEnrollment(student("在读"))).isNull();
        assertThat(RuleEngine.checkEnrollment(student(null))).isNull();
        RuleEngine.ApplicationCheck c = RuleEngine.validateStudentApplication(student("在读"), job(), List.of(),
                List.of(), Map.of());
        assertThat(c.passed()).isTrue();
        // 在读时不额外列出学籍项，保持原有校验项顺序
        assertThat(c.checks()).extracting(RuleEngine.Check::key).containsExactly("hours");
    }

    @Test
    void suspendedOrGraduatedIsHardFailure() {
        for (String s : List.of("休学", "毕业")) {
            RuleEngine.ApplicationCheck c = RuleEngine.validateStudentApplication(student(s), job(), List.of(),
                    List.of(), Map.of());
            assertThat(c.passed()).isFalse();
            assertThat(c.canSpecialApprove()).isFalse();
            assertThat(c.specialBlockedReason()).isEqualTo("学籍状态校验（在读）未通过，属于硬性条件，不可特批");
            RuleEngine.Check first = c.checks().get(0);
            assertThat(first.key()).isEqualTo("enrollment");
            assertThat(first.overridable()).isFalse();
            assertThat(first.detail()).isEqualTo("当前学籍状态为「" + s + "」，仅在读学生可申请三助岗位（硬性条件，不可特批）");
        }
    }

    @Test
    void hireIsBlockedForSuspendedStudent() {
        JobApplication app = new JobApplication();
        app.setStatus("SUBMITTED");
        RuleEngine.HireCheck h = RuleEngine.validateHire(app, job(), student("休学"), List.of(), Map.of());
        assertThat(h.ok()).isFalse();
        assertThat(h.errors()).containsExactly("该学生当前学籍状态为「休学」，仅在读学生可录用（不可特批）");
        assertThat(RuleEngine.validateHire(app, job(), student("在读"), List.of(), Map.of()).ok()).isTrue();
    }
}
