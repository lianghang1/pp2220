package io.github.lianghang1.conflux.pay.channels.chinaums.paymethod;

import io.github.lianghang1.conflux.pay.channel.PayMethod;

/**
 * 天满云闪付 APP支付：端点 {@code /v1/netpay/uac/app-order}，instMid=APPDEFAULT，支付要素取 {@code appPayRequest}。
 *
 * @author lianghang
 */
public class ChinaumsUacAppPayMethod extends AbstractChinaumsPayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_UAC_APP.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/uac/app-order";
    }

    @Override
    protected String instMid() {
        return "APPDEFAULT";
    }

    @Override
    protected String payloadField() {
        return "appPayRequest";
    }

    @Override
    protected PayloadKind payloadKind() {
        return PayloadKind.APP;
    }
}
