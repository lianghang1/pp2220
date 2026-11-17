package io.github.lianghang1.conflux.pay.example.web;

import io.github.lianghang1.conflux.pay.api.PaymentQueryRequest;
import io.github.lianghang1.conflux.pay.api.PaymentRequest;
import io.github.lianghang1.conflux.pay.api.PaymentResult;
import io.github.lianghang1.conflux.pay.api.ProfitShareRequest;
import io.github.lianghang1.conflux.pay.api.ProfitShareResult;
import io.github.lianghang1.conflux.pay.api.RefundQueryRequest;
import io.github.lianghang1.conflux.pay.api.RefundRequest;
import io.github.lianghang1.conflux.pay.api.RefundResult;
import io.github.lianghang1.conflux.pay.api.TransferRequest;
import io.github.lianghang1.conflux.pay.api.TransferResult;
import io.github.lianghang1.conflux.pay.engine.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付网关统一 API 演示入口：直接透传请求到网关门面。
 *
 * <p>SDK 只返回渠道归一结果；落库、状态推进、幂等判重与业务联动
 * （更新订单、通知库存等）由使用方在拿到结果后自行完成。</p>
 *
 * @author lianghang
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PayController {

    private final PaymentGateway paymentGateway;

    @PostMapping("/pay")
    public R<PaymentResult> pay(@RequestBody PaymentRequest request) {
        return R.ok(paymentGateway.createPayment(request));
    }

    @PostMapping("/pay/query")
    public R<PaymentResult> queryPay(@RequestBody PaymentQueryRequest request) {
        return R.ok(paymentGateway.queryPayment(request));
    }

    @PostMapping("/pay/close")
    public R<PaymentResult> close(@RequestBody PaymentQueryRequest request) {
        return R.ok(paymentGateway.closePayment(request));
    }

    @PostMapping("/refund")
    public R<RefundResult> refund(@RequestBody RefundRequest request) {
        return R.ok(paymentGateway.createRefund(request));
    }

    @PostMapping("/refund/query")
    public R<RefundResult> queryRefund(@RequestBody RefundQueryRequest request) {
        return R.ok(paymentGateway.queryRefund(request));
    }

    @PostMapping("/transfer")
    public R<TransferResult> transfer(@RequestBody TransferRequest request) {
        return R.ok(paymentGateway.createTransfer(request));
    }

    @PostMapping("/profit-share")
    public R<ProfitShareResult> profitShare(@RequestBody ProfitShareRequest request) {
        return R.ok(paymentGateway.createProfitShare(request));
    }
}
