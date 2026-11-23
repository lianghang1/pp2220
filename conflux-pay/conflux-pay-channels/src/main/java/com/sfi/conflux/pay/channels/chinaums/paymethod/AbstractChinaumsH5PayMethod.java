package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channels.chinaums.ChinaumsClient;
import com.sfi.conflux.pay.channels.chinaums.ChinaumsParams;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天满 H5 下单抽象（三种 H5 均为 GET 跳转 + OPEN-FORM-PARAM 认证）。
 *
 * <p>天满 H5 为 GET 支付链接：构造带签名的完整跳转 URL，交给使用方浏览器跳转。
 * 这是官方文档"最终生成的链接地址…输入浏览器跳转"的直接映射，不臆造响应字段。</p>
 *
 * @author lianghang
 */
public abstract class AbstractChinaumsH5PayMethod implements ChinaumsPayMethod {

    protected abstract String endpoint();

    protected abstract String instMid();

    /** 是否微信 H5（需 subAppId 与 sceneType 等）。 */
    protected boolean needSubAppId() {
        return false;
    }

    @Override
    public PaymentInvocation createPayment(Payment payment, ChannelConfig config, ChinaumsClient client) {
        ChinaumsParams params = ChinaumsClient.paramsOf(config);
        Map<String, Object> business = new LinkedHashMap<String, Object>();
        business.put("merOrderId", payment.getBizOrderNo());
        business.put("totalAmount", payment.getAmount());
        business.put("instMid", instMid());
        String notifyUrl = StringUtils.defaultIfBlank(payment.getNotifyUrl(), config.getNotifyUrl());
        if (StringUtils.isNotBlank(notifyUrl)) {
            business.put("notifyUrl", notifyUrl);
        }
        if (StringUtils.isNotBlank(payment.getSubject())) {
            business.put("orderDesc", payment.getSubject());
        }
        if (StringUtils.isNotBlank(payment.getClientIp())) {
            business.put("clientIp", payment.getClientIp());
        }
        if (needSubAppId() && StringUtils.isNotBlank(params.getSubAppId())) {
            business.put("subAppId", params.getSubAppId());
        }
        fillExtra(payment, config, business);
        String url = client.buildGetUrl(endpoint(), config, business);
        return new PaymentInvocation()
                .setState(PaymentState.PAYING)
                .setPayload(PayPayload.redirect(url));
    }

    protected void fillExtra(Payment payment, ChannelConfig config, Map<String, Object> business) {
    }
}
