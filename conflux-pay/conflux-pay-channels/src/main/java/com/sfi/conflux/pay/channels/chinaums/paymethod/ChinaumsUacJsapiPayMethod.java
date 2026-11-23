package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.pay.channel.PayMethod;

/**
 * 天满云闪付 公众号支付：端点 {@code /v1/netpay/acp/js-pay}，instMid=YUEDANDEFAULT，支付要素取 {@code jsPayRequest}。
 *
 * @author lianghang
 */
public class ChinaumsUacJsapiPayMethod extends AbstractChinaumsPayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_UAC_JSAPI.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/acp/js-pay";
    }

    @Override
    protected String instMid() {
        return "YUEDANDEFAULT";
    }

    @Override
    protected String tradeType() {
        return "JSAPI";
    }

    @Override
    protected String payloadField() {
        return "jsPayRequest";
    }

    @Override
    protected PayloadKind payloadKind() {
        return PayloadKind.JSAPI;
    }
}
