package io.github.lianghang1.paygateway.example.job;

import io.github.lianghang1.paygateway.api.PaymentQueryRequest;
import io.github.lianghang1.paygateway.engine.PaymentGateway;
import io.github.lianghang1.paygateway.model.Payment;
import io.github.lianghang1.paygateway.store.PaymentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兜底定时任务演示：下游通知重试投递 + 支付中订单补单。
 * 生产项目可按需换成分布式调度框架，逻辑保持一致。
 *
 * @author lianghang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayHousekeepingJobs {

    private final PaymentGateway paymentGateway;
    private final PaymentStore paymentStore;

    /** 每 10 秒投递到期未完成的下游通知。 */
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void retryPendingNotifies() {
        paymentGateway.retryPendingNotifies(100);
    }

    /** 每 30 秒对超时未终态的支付单发起渠道复核（补单）。 */
    @Scheduled(fixedDelay = 30000, initialDelay = 20000)
    public void reissuePayingOrders() {
        List<Payment> pending = paymentStore.findPayingBefore(LocalDateTime.now().minusMinutes(2), 50);
        for (Payment payment : pending) {
            try {
                paymentGateway.queryPayment(new PaymentQueryRequest().setPaymentId(payment.getPaymentId()));
            } catch (Exception e) {
                log.warn("补单失败: paymentId={}, error={}", payment.getPaymentId(), e.getMessage());
            }
        }
    }
}
