package edu.cga.service;

import edu.cga.common.BizException;
import edu.cga.common.TimeService;
import edu.cga.domain.entity.Contract;
import edu.cga.domain.entity.User;
import edu.cga.domain.mapper.ContractMapper;
import edu.cga.domain.mapper.UserMapper;
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

    public ContractService(ContractMapper contractMapper, UserMapper userMapper, DataScope scope, AuditService audit,
                           TimeService time) {
        this.contractMapper = contractMapper;
        this.userMapper = userMapper;
        this.scope = scope;
        this.audit = audit;
        this.time = time;
    }

    /** 合同所属学生本人签署；状态须为 PENDING_SIGN；国际生必须勾选合规声明 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Contract sign(String contractId, String signatureDataUrl, boolean complianceAgreed, CurrentUser operator) {
        // 学生的数据范围只含自己的合同，别人的合同按不存在处理
        scope.requireVisible(contractMapper, scope.contracts(operator), contractId, "电子协议不存在");
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
        User student = userMapper.selectById(operator.id());
        boolean intl = Boolean.TRUE.equals(student.getIsInternational());
        if (intl && !complianceAgreed) {
            throw BizException.badRequest("国际生必须勾选《国际生合法工时合规声明》方可生效");
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
}
