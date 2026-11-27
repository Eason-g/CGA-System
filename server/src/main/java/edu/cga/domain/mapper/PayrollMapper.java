package edu.cga.domain.mapper;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.cga.domain.entity.Payroll;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PayrollMapper extends BaseMapper<Payroll> {

    /** 锁住薪酬单：发放前使用（调用方先锁经费行再锁薪酬行，顺序固定避免死锁） */
    @Select("SELECT * FROM payrolls WHERE id = #{id} FOR UPDATE")
    Payroll lockById(@Param("id") String id);

    /** 某合同某月的首张薪酬单。 */
    @Select("SELECT * FROM payrolls WHERE contract_id = #{contractId} AND month = #{month} ORDER BY id LIMIT 1")
    Payroll selectByContractAndMonth(@Param("contractId") String contractId, @Param("month") String month);

    /** 某合同某月所有批次。调用方在计算或改动批次前须锁住经费行。 */
    @Select("SELECT * FROM payrolls WHERE contract_id = #{contractId} AND month = #{month} ORDER BY id")
    List<Payroll> selectBatches(@Param("contractId") String contractId, @Param("month") String month);

    /** 某合同最近一张未发放的薪酬单，加锁（考核修改系数时使用） */
    // 已提交财务（SUBMITTED）的金额已发给财务系统，不能再改系数
    @Select("SELECT * FROM payrolls WHERE contract_id = #{contractId} AND finance_status NOT IN ('PAID', 'SUBMITTED')"
            + " ORDER BY month DESC, id DESC LIMIT 1 FOR UPDATE")
    Payroll lockLatestUnpaidByContract(@Param("contractId") String contractId);

    /**
     * 预算汇总：某岗位下已发放 + 待发放薪酬之和，可排除一张（重新核算/改系数时排除它自己）。
     * 岗位剩余预留 = reservedAmount − 该值（不低于 0）。
     * 待发放包括 PENDING_PAY、已提交财务 SUBMITTED、发放失败待重提 PAY_FAILED（P1 第三批）。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM payrolls"
            + " WHERE job_id = #{jobId} AND finance_status IN ('PAID', 'PENDING_PAY', 'SUBMITTED', 'PAY_FAILED')"
            + " AND (#{excludeId} IS NULL OR id <> #{excludeId})")
    BigDecimal sumCommittedByJob(@Param("jobId") String jobId, @Param("excludeId") String excludeId);

    /**
     * 预算汇总：某经费池下，待发放薪酬中超出各自岗位预留的金额之和（可排除一张）。
     * 每个岗位的未发放超额 = max(已发放+待发放 − 预留, 0) − max(已发放 − 预留, 0)；
     * 已发放的超额已经计入 used，这里只统计尚未发放、但已承诺要花掉的那部分可用额。
     */
    @Select("SELECT COALESCE(SUM(GREATEST(c.committed - j.reserved_amount, 0)"
            + " - GREATEST(c.paid - j.reserved_amount, 0)), 0)"
            + " FROM (SELECT job_id, SUM(amount) AS committed,"
            + "         SUM(CASE WHEN finance_status = 'PAID' THEN amount ELSE 0 END) AS paid"
            + "       FROM payrolls"
            + "       WHERE budget_id = #{budgetId}"
            + "         AND finance_status IN ('PAID', 'PENDING_PAY', 'SUBMITTED', 'PAY_FAILED')"
            + "         AND (#{excludeId} IS NULL OR id <> #{excludeId})"
            + "       GROUP BY job_id) c"
            + " JOIN jobs j ON j.id = c.job_id")
    BigDecimal sumUnpaidExcessByBudget(@Param("budgetId") String budgetId, @Param("excludeId") String excludeId);

    /** 预算汇总：某岗位下已发放薪酬之和（发放时计算“发放前该岗位剩余预留”） */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM payrolls WHERE job_id = #{jobId} AND finance_status = 'PAID'")
    BigDecimal sumPaidByJob(@Param("jobId") String jobId);
}
