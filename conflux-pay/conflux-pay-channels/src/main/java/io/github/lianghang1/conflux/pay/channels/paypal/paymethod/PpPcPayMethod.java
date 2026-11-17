package io.github.lianghang1.conflux.pay.channels.paypal.paymethod;

import io.github.lianghang1.conflux.pay.api.PayPayload;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.paypal.PayPalClient;
import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.pay.channels.paypal.PayPalTranslator;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PayPal 电脑网页支付（Orders v2 下单，intent=CAPTURE）：返回 approve 链接
 * （REDIRECT），用户批准后由使用方查询触发自动捕获。
 *
 * <p>purchase_units 的 {@code custom_id} 固化为网关支付单号，Webhook 回调据此定位。</p>
 *
 * @author lianghang
 */
public class PpPcPayMethod implements PayPalPayMethod {

    @Override
    public String method() {
        return PayMethod.PAYPAL_SALE.getCode();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, PayPalClient client) {
        Map<String, Object> order = new HashMap<String, Object>();
        order.put("intent", "CAPTURE");
        Map<String, Object> unit = new HashMap<String, Object>();
        unit.put("reference_id", payment.getBizOrderNo());
        unit.put("custom_id", payment.getBizOrderNo());
        Map<String, Object> amount = new HashMap<String, Object>();
        amount.put("currency_code", payment.getCurrency());
        amount.put("value", PayPalTranslator.toYuan(payment.getAmount()));
        unit.put("amount", amount);
        unit.put("description", StringUtils.defaultIfBlank(payment.getSubject(), "conflux-pay"));
        List<Map<String, Object>> units = new ArrayList<Map<String, Object>>();
        units.add(unit);
        order.put("purchase_units", units);

        Map<String, Object> result = client.execute("下单", config, "POST", "/v2/checkout/orders", order);
        String orderId = PayPalTranslator.text(result.get("id"));
        String approveUrl = PayPalTranslator.link(result, "approve");
        return new PaymentInvocation()
                .setChannelOrderNo(orderId)
                .setPayload(PayPayload.redirect(approveUrl))
                .setState(PaymentState.PAYING);
    }
}
