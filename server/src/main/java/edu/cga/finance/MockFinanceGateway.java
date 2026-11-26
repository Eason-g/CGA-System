package edu.cga.finance;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import edu.cga.common.BizException;
import edu.cga.common.TimeService;
import edu.cga.config.MaterialFinanceProperties;
import edu.cga.service.FinanceService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 模拟学校财务系统（演示用）：
 * <ul>
 *   <li>受理付款指令后，若开启 app.finance.mock.auto-callback，则在提交事务提交后由定时任务延迟几秒自动回调；</li>
 *   <li>回调成功率由 app.finance.mock.success-rate 控制（默认 100%）；</li>
 *   <li>回调与真实财务系统一样：构造 JSON 请求体、用 app.finance.callback-secret 做 HMAC-SHA256 签名，
 *       再交给与 POST /api/integration/finance/callback 相同的处理入口（同样校验签名、按流水号幂等）。</li>
 * </ul>
 */
@Component
public class MockFinanceGateway implements FinanceGateway {

    private static final Logger log = LoggerFactory.getLogger(MockFinanceGateway.class);

    private final MaterialFinanceProperties props;
    private final ObjectProvider<FinanceService> financeService;
    private final JsonMapper json;
    private final TimeService time;
    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mock-finance-callback");
        t.setDaemon(true);
        return t;
    });

    public MockFinanceGateway(MaterialFinanceProperties props, ObjectProvider<FinanceService> financeService,
                              JsonMapper json, TimeService time) {
        this.props = props;
        this.financeService = financeService;
        this.json = json;
        this.time = time;
    }

    @Override
    public void submit(PaymentOrder order) {
        MaterialFinanceProperties.Mock mock = props.getFinance().getMock();
        if (!mock.isAutoCallback()) {
            return;
        }
        Runnable schedule = () -> scheduler.schedule(() -> autoCallback(order),
                Math.max(0, mock.getCallbackDelay().toMillis()), TimeUnit.MILLISECONDS);
        // 等提交事务真正提交后再排期，避免回调时读不到 SUBMITTED 状态
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule.run();
                }
            });
        } else {
            schedule.run();
        }
    }

    private void autoCallback(PaymentOrder order) {
        boolean success = random.nextDouble() < props.getFinance().getMock().getSuccessRate();
        try {
            simulateCallback(order.serialNo(), success, success ? null : "模拟财务系统：收款账户信息校验失败，已退回");
        } catch (BizException e) {
            // 例如演示数据已被重置、流水号不存在
            log.warn("模拟财务自动回调未处理（流水号 {}）：{}", order.serialNo(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("模拟财务自动回调异常（流水号 {}）", order.serialNo(), e);
        }
    }

    @Override
    public boolean supportsSimulation() {
        return true;
    }

    /** 构造已签名的回调请求并交给回调处理入口 */
    @Override
    public Object simulateCallback(String serialNo, boolean success, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serialNo", serialNo);
        body.put("success", success);
        body.put("voucherNo", success ? "CW-" + time.today().toString().replace("-", "").substring(0, 6) + "-"
                + (1000 + random.nextInt(9000)) : null);
        body.put("reason", success ? null : (reason == null || reason.isBlank() ? "模拟财务系统：退回" : reason));
        body.put("timestamp", time.nowTimestamp());
        String raw = json.writeValueAsString(body);
        String signature = FinanceSignature.sign(props.getFinance().getCallbackSecret(), raw);
        return financeService.getObject().handleCallback(raw, signature);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
