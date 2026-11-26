package edu.cga.finance;

import java.math.BigDecimal;

/**
 * 学校财务系统对接接口（P1 第三批 3.3）。
 * <p>
 * 发放分两步：本系统调用 {@link #submit} 提交付款指令（薪酬状态 PENDING_PAY → SUBMITTED，经费不变）；
 * 财务系统处理完成后调用 POST /api/integration/finance/callback 回写结果（签名见 {@link FinanceSignature}）。
 * 默认实现为 {@link MockFinanceGateway}；对接真实财务系统时替换为新的实现即可。
 */
public interface FinanceGateway {

    /** 付款指令 */
    record PaymentOrder(String serialNo, String payrollId, String studentId, String month, BigDecimal amount) {
    }

    /**
     * 提交付款指令。方法返回即表示财务系统已受理；处理结果通过回调异步返回。
     * 在业务事务内调用，抛出异常会使本次提交回滚。
     */
    void submit(PaymentOrder order);

    /** 是否支持“模拟财务回调”（演示用，只有模拟网关支持） */
    default boolean supportsSimulation() {
        return false;
    }

    /**
     * 模拟财务系统发起一次已签名的回调（演示用）。
     *
     * @return 回调处理结果
     */
    default Object simulateCallback(String serialNo, boolean success, String reason) {
        throw new UnsupportedOperationException("当前财务网关不支持模拟回调");
    }
}
