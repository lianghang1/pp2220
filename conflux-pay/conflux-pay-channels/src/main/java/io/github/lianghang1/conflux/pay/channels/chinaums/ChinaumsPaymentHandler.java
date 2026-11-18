package io.github.lianghang1.conflux.pay.channels.chinaums;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.PaymentHandler;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PaymentSnapshot;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsAliAppPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsAliH5PayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsAliJsapiPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsAliMiniPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsQrPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsUacAppPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsUacH5PayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsUacJsapiPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsUacMiniPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsWxAppPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsWxH5PayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsWxJsapiPayMethod;
import io.github.lianghang1.conflux.pay.channels.chinaums.paymethod.ChinaumsWxMiniPayMethod;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 天满支付处理器门面：按支付方式路由到对应 PayMethod（下单），
 * 查单/关单按商户订单号统一走 netpay 公共端点。
 *
 * @author lianghang
 */
@Slf4j
public class ChinaumsPaymentHandler implements PaymentHandler {

    private final ChinaumsClient client = new ChinaumsClient();
    private final Map<String, ChinaumsPayMethod> payMethods = new HashMap<String, ChinaumsPayMethod>();

    public ChinaumsPaymentHandler() {
        register(new ChinaumsWxAppPayMethod());
        register(new ChinaumsWxH5PayMethod());
        register(new ChinaumsWxMiniPayMethod());
        register(new ChinaumsWxJsapiPayMethod());
        register(new ChinaumsAliAppPayMethod());
        register(new ChinaumsAliH5PayMethod());
        register(new ChinaumsAliMiniPayMethod());
        register(new ChinaumsAliJsapiPayMethod());
        register(new ChinaumsUacAppPayMethod());
        register(new ChinaumsUacH5PayMethod());
        register(new ChinaumsUacMiniPayMethod());
        register(new ChinaumsUacJsapiPayMethod());
        register(new ChinaumsQrPayMethod());
    }

    private void register(ChinaumsPayMethod payMethod) {
        payMethods.put(payMethod.method(), payMethod);
    }

    public Set<String> supportedMethods() {
        return payMethods.keySet();
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
        ChinaumsPayMethod payMethod = payMethods.get(payment.getMethod());
        if (payMethod == null) {
            throw new GatewayException(GatewayErrorCode.METHOD_NOT_SUPPORTED,
                    "天满渠道不支持支付方式: {}", payment.getMethod());
        }
        return payMethod.createPayment(payment, config, client);
    }

    @Override
    public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
        Map<String, Object> business = new LinkedHashMap<String, Object>();
        business.put("merOrderId", payment.getBizOrderNo());
        if (StringUtils.isNotBlank(payment.getChannelOrderNo())) {
            business.put("targetOrderId", payment.getChannelOrderNo());
        }
        JsonNode root = client.execute("查单", ChinaumsClient.API_QUERY, config, business);
        PaymentSnapshot snapshot = new PaymentSnapshot()
                .setChannelOrderNo(ChinaumsClient.text(root, "targetOrderId"))
                .setState(ChinaumsStateTranslator.toPaymentState(ChinaumsClient.text(root, "status")))
                .setPaidAmount(ChinaumsClient.longValue(root, "totalAmount"))
                .setSucceedTime(ChinaumsStateTranslator.toLocalDateTime(ChinaumsClient.text(root, "payTime")))
                .setRawMsg(ChinaumsClient.text(root, "status"));
        if (snapshot.getChannelOrderNo() == null) {
            snapshot.setChannelOrderNo(ChinaumsClient.text(root, "merOrderId"));
        }
        return snapshot;
    }

    @Override
    public void closePayment(Payment payment, ChannelConfig config) {
        Map<String, Object> business = new LinkedHashMap<String, Object>();
        business.put("merOrderId", payment.getBizOrderNo());
        client.execute("关单", ChinaumsClient.API_CLOSE, config, business);
        log.info("天满关单完成: merOrderId={}", payment.getBizOrderNo());
    }
}
