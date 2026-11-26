package edu.cga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import edu.cga.domain.entity.Budget;
import edu.cga.domain.entity.FinanceTransaction;
import edu.cga.domain.entity.Payroll;
import edu.cga.domain.mapper.FinanceTransactionMapper;
import edu.cga.rule.RuleEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * P1 第三批 3.3 财务对接：提交财务 → 回调回写（签名、幂等、失败重提），经费按 P0 发放规则变化。
 * 测试中关闭了模拟财务的自动回调，直接调用公开回调接口。
 */
class FinanceIntegrationTest extends MaterialFinanceTestBase {

    private static final String SCHOOL = "zhou.guoqiang";

    @Autowired
    private FinanceTransactionMapper txnMapper;

    private String submit(String payrollId) throws Exception {
        JsonNode r = apiPost("/api/payrolls/" + payrollId + "/submit-finance", SCHOOL, null, 200);
        assertThat(r.path("data").path("financeStatus").asString()).isEqualTo("SUBMITTED");
        String serial = r.path("data").path("financeSerialNo").asString();
        assertThat(serial).startsWith("FIN");
        return serial;
    }

    private static void assertBudgetUnchanged(Budget before, Budget after) {
        assertThat(after.getUsedAmount()).isEqualByComparingTo(before.getUsedAmount());
        assertThat(after.getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount());
    }

    @Test
    void successCallbackPaysOnceAndDeductsBudgetByP0Rules() throws Exception {
        Payroll pay = payrollMapper.selectById("PAY_03"); // PENDING_PAY，B_CS_RA，JOB_04
        Budget before = budget(pay.getBudgetId());
        BigDecimal reserved = jobMapper.selectById(pay.getJobId()).getReservedAmount();
        BigDecimal release = RuleEngine.payoutFrozenRelease(pay.getAmount(), reserved,
                payrollMapper.sumPaidByJob(pay.getJobId()));

        String serial = submit("PAY_03");
        assertBudgetUnchanged(before, budget(pay.getBudgetId()));
        assertThat(txnMapper.selectById(serial).getStatus()).isEqualTo(FinanceTransaction.SUBMITTED);
        // 已提交财务的不能再走一步发放，也不能重复提交
        apiPost("/api/payrolls/PAY_03/payout", SCHOOL, null, 400);
        apiPost("/api/payrolls/PAY_03/submit-finance", SCHOOL, null, 400);

        String body = callbackBody(serial, true, "CW-202611-8888", null);
        JsonNode r = financeCallback(body, null, 200);
        assertThat(r.path("data").path("duplicate").asBoolean()).isFalse();
        Payroll paid = payrollMapper.selectById("PAY_03");
        assertThat(paid.getFinanceStatus()).isEqualTo("PAID");
        assertThat(paid.getVoucherNo()).isEqualTo("CW-202611-8888");
        Budget after = budget(pay.getBudgetId());
        assertThat(after.getUsedAmount()).isEqualByComparingTo(before.getUsedAmount().add(pay.getAmount()));
        assertThat(after.getFrozenAmount()).isEqualByComparingTo(before.getFrozenAmount().subtract(release));

        // 重复回调（同一流水号，哪怕结果不同）不重复扣款
        long logs = auditCount();
        JsonNode dup = financeCallback(body, null, 200);
        assertThat(dup.path("data").path("duplicate").asBoolean()).isTrue();
        financeCallback(callbackBody(serial, false, null, "迟到的失败"), null, 200);
        assertBudgetUnchanged(after, budget(pay.getBudgetId()));
        assertThat(payrollMapper.selectById("PAY_03").getFinanceStatus()).isEqualTo("PAID");
        assertThat(auditCount()).isEqualTo(logs);
    }

    @Test
    void failedCallbackKeepsBudgetAndCanBeResubmitted() throws Exception {
        Payroll pay = payrollMapper.selectById("PAY_05"); // PENDING_PAY，B_CS_TA
        Budget before = budget(pay.getBudgetId());
        String serial1 = submit("PAY_05");

        financeCallback(callbackBody(serial1, false, null, "收款账户户名不符"), null, 200);
        Payroll failed = payrollMapper.selectById("PAY_05");
        assertThat(failed.getFinanceStatus()).isEqualTo("PAY_FAILED");
        assertThat(failed.getFailReason()).isEqualTo("收款账户户名不符");
        assertBudgetUnchanged(before, budget(pay.getBudgetId()));
        assertThat(txnMapper.selectById(serial1).getStatus()).isEqualTo(FinanceTransaction.FAILED);

        // 重新提交：新流水号，经费仍不变
        String serial2 = submit("PAY_05");
        assertThat(serial2).isNotEqualTo(serial1);
        assertBudgetUnchanged(before, budget(pay.getBudgetId()));
        // 旧流水号的回调（哪怕声称成功）被当作重复，不会扣款
        JsonNode stale = financeCallback(callbackBody(serial1, true, "CW-OLD", null), null, 200);
        assertThat(stale.path("data").path("duplicate").asBoolean()).isTrue();
        assertBudgetUnchanged(before, budget(pay.getBudgetId()));

        financeCallback(callbackBody(serial2, true, null, null), null, 200);
        Payroll paid = payrollMapper.selectById("PAY_05");
        assertThat(paid.getFinanceStatus()).isEqualTo("PAID");
        assertThat(paid.getVoucherNo()).isNotBlank();
        assertThat(budget(pay.getBudgetId()).getUsedAmount())
                .isEqualByComparingTo(before.getUsedAmount().add(pay.getAmount()));
    }

    @Test
    void badSignatureIsRejected() throws Exception {
        Payroll pay = payrollMapper.selectById("PAY_06");
        Budget before = budget(pay.getBudgetId());
        String serial = submit("PAY_06");
        String body = callbackBody(serial, true, "CW-X", null);

        financeCallback(body, "0".repeat(64), 401);
        financeCallback(body, "", 401);
        // 签名正确但请求体被篡改
        String sig = edu.cga.finance.FinanceSignature.sign(CALLBACK_SECRET, body);
        financeCallback(body.replace("CW-X", "CW-Y"), sig, 401);
        // 用错误密钥签名
        financeCallback(body, edu.cga.finance.FinanceSignature.sign("wrong-secret", body), 401);

        assertThat(payrollMapper.selectById("PAY_06").getFinanceStatus()).isEqualTo("SUBMITTED");
        assertBudgetUnchanged(before, budget(pay.getBudgetId()));

        // 演示用“模拟财务回调”：走同一入口（同样签名），失败后可重提
        apiPost("/api/payrolls/PAY_06/simulate-finance-callback", SCHOOL, Map.of("success", false, "reason", "演示失败"),
                200);
        assertThat(payrollMapper.selectById("PAY_06").getFinanceStatus()).isEqualTo("PAY_FAILED");
        assertBudgetUnchanged(before, budget(pay.getBudgetId()));
        apiPost("/api/payrolls/PAY_06/simulate-finance-callback", SCHOOL, Map.of("success", true), 400);
    }

    @Test
    void onlySchoolAdminCanSubmitOrSimulate() throws Exception {
        apiPost("/api/payrolls/PAY_06/submit-finance", "li.wanqing", null, 403);
        apiPost("/api/payrolls/PAY_06/submit-finance", "zhang.mingyuan", null, 403);
        apiPost("/api/payrolls/PAY_06/submit-finance", "alex.chen", null, 403);
        apiPost("/api/payrolls/PAY_06/submit-finance", null, null, 401);
        apiPost("/api/payrolls/PAY_06/simulate-finance-callback", "li.wanqing", Map.of("success", true), 403);
        // 未核算的不能提交；流水号不存在 404
        apiPost("/api/payrolls/PAY_04/submit-finance", SCHOOL, null, 400);
        financeCallback(callbackBody("FIN_NOT_EXIST", true, null, null), null, 404);
    }
}
