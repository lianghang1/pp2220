package io.github.lianghang1.conflux.pay.channels.chinaums.paymethod;

import io.github.lianghang1.conflux.pay.channel.PayMethod;

/**
 * 天满支付宝 公众号支付：端点 {@code /v1/netpay/trade/create}，instMid=YUEDANDEFAULT，支付要素取 {@code jsPayRequest}。
 *
 * @author lianghang
 */
public class ChinaumsAliJsapiPayMethod extends AbstractChinaumsPayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_ALI_JSAPI.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/trade/create";
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
