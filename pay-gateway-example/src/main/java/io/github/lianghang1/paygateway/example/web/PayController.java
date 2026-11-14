package io.github.lianghang1.paygateway.example.web;

import io.github.lianghang1.paygateway.api.PaymentQueryRequest;
import io.github.lianghang1.paygateway.api.PaymentRequest;
import io.github.lianghang1.paygateway.api.PaymentResult;
import io.github.lianghang1.paygateway.api.ProfitShareRequest;
import io.github.lianghang1.paygateway.api.ProfitShareResult;
import io.github.lianghang1.paygateway.api.RefundRequest;
import io.github.lianghang1.paygateway.api.RefundResult;
import io.github.lianghang1.paygateway.api.TransferRequest;
import io.github.lianghang1.paygateway.api.TransferResult;
import io.github.lianghang1.paygateway.engine.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付网关统一 API 演示入口：直接透传请求到网关门面。
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

    @GetMapping("/pay/{paymentId}")
    public R<PaymentResult> getPay(@PathVariable String paymentId) {
        return R.ok(paymentGateway.queryPayment(new PaymentQueryRequest().setPaymentId(paymentId)));
    }

    @PostMapping("/pay/{paymentId}/close")
    public R<PaymentResult> close(@PathVariable String paymentId) {
        return R.ok(paymentGateway.closePayment(paymentId));
    }

    @PostMapping("/refund")
    public R<RefundResult> refund(@RequestBody RefundRequest request) {
        return R.ok(paymentGateway.createRefund(request));
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
