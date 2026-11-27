package edu.cga.service;

import edu.cga.common.BiText;
import edu.cga.common.BizException;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.JobApplication;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.JobApplicationMapper;
import edu.cga.domain.mapper.UserMapper;
import edu.cga.rule.RuleEngine;
import edu.cga.scope.DataScope;
import edu.cga.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 电子聘用协议签署（signContract）。
 */
@Service
public class ContractService {

    /** 签名图片 dataURL 上限（约 2MB），防止异常大的请求写进数据库 */
    private static final int MAX_SIGNATURE_LENGTH = 2 * 1024 * 1024;

    private final ContractMapper contractMapper;
    private final UserMapper userMapper;
    private final DataScope scope;
    private final AuditService audit;
    private final TimeService time;
    private final ConfigService configService;
    private final JobApplicationMapper applicationMapper;

    public ContractService(ContractMapper contractMapper, UserMapper userMapper, DataScope scope, AuditService audit,
                           TimeService time, ConfigService configService,
                           JobApplicationMapper applicationMapper) {
        this.contractMapper = contractMapper;
        this.userMapper = userMapper;
        this.scope = scope;
        this.audit = audit;
        this.time = time;
        this.configService = configService;
        this.applicationMapper = applicationMapper;
    }

    /** 合同所属学生本人签署；状态须为 PENDING_SIGN；国际生必须勾选合规声明 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Contract sign(String contractId, String signatureDataUrl, boolean complianceAgreed, CurrentUser operator) {
        // 学生的数据范围只含自己的合同，别人的合同按不存在处理
        scope.requireVisible(contractMapper, scope.contracts(operator), contractId, "电子协议不存在");
        User student = userMapper.lockById(operator.id());
        if (student == null) {
            throw BizException.notFound("学生不存在");
        }
        Contract con = contractMapper.lockById(contractId);
        if (!operator.id().equals(con.getStudentId())) {
            throw BizException.notFound("电子协议不存在");
        }
        if (!"PENDING_SIGN".equals(con.getStatus())) {
            throw BizException.badRequest("该电子协议当前不处于待签署状态");
        }
        if (signatureDataUrl == null || signatureDataUrl.isBlank()) {
            throw BizException.badRequest("请先在签名板手写确认签名");
        }
        if (!signatureDataUrl.startsWith("data:image/") || signatureDataUrl.length() > MAX_SIGNATURE_LENGTH) {
            throw BizException.badRequest("签名数据格式不正确，请重新手写签名");
        }
        if (con.getEndDate() != null && con.getEndDate().isBefore(time.today())) {
            throw BizException.badRequest("聘用协议已过期", "The employment contract has expired");
        }
        BiText eligibility = RuleEngine.workingEligibilityText(student, con.getEndDate());
        if (eligibility != null) {
            throw BizException.forbidden(eligibility);
        }
        boolean intl = Boolean.TRUE.equals(student.getIsInternational());
        if (intl && !complianceAgreed) {
            throw BizException.badRequest("国际生必须勾选《国际生合法工时合规声明》方可生效",
                    "International students must tick the International Student Legal Working Hours Compliance Declaration"
                            + " for the contract to take effect");
        }

        // 签署前按聘期复核跨岗工时；超限时只认已通过的特批，且不超过特批时记录的工时（与录用复核同一口径）
        int limit = RuleEngine.getMaxWeeklyHours(configService.globalRulesForWrite(), student);
        int peak = RuleEngine.getStudentAllocatedWeeklyHours(
                contractMapper.selectAllocatingByStudent(student.getId()), student.getId(),
                con.getStartDate(), con.getEndDate());
        if (peak > limit && !withinApprovedSpecialCeiling(con, peak)) {
            throw BizException.forbidden("跨岗累计周工时 " + peak + "h 超出上限 " + limit + "h/周，且无对应特批，不能签署",
                    "Cross-job weekly hours of " + peak + "h exceed the limit of " + limit
                            + "h/week with no matching special approval; cannot sign");
        }

        con.setStatus("ACTIVE");
        con.setSignedAt(time.now());
        con.setSignatureDataUrl(signatureDataUrl);
        con.setSignatureText(student.getName());
        con.setComplianceAgreed(true);
        contractMapper.updateById(con);

        audit.log("CONTRACT", con.getId(), operator, "签署电子聘用协议",
                intl ? "已完成手写电子签名并勾选确认《国际生合法工时合规声明》" : "已完成手写电子签名，聘用协议正式生效",
                "PENDING_SIGN", "ACTIVE");
        return con;
    }

    /** 合同对应的申请已获特批，且当前峰值不超过特批时记录的 projectedHours */
    private boolean withinApprovedSpecialCeiling(Contract con, int peak) {
        if (con.getApplicationId() == null) {
            return false;
        }
        JobApplication app = applicationMapper.selectById(con.getApplicationId());
        if (app == null || !"APPROVED".equals(app.getSpecialStatus()) || app.getComplianceCheckResult() == null) {
            return false;
        }
        java.math.BigDecimal ceiling = edu.cga.common.Num.toDecimal(app.getComplianceCheckResult().get("projectedHours"));
        return ceiling != null && java.math.BigDecimal.valueOf(peak).compareTo(ceiling) <= 0;
    }
}
