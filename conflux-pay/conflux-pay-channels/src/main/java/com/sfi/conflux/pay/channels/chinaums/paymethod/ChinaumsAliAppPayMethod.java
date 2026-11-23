package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.pay.channel.PayMethod;

/**
 * 天满支付宝 APP支付：端点 {@code /v1/netpay/trade/precreate}，instMid=APPDEFAULT，支付要素取 {@code appPayRequest}。
 *
 * @author lianghang
 */
public class ChinaumsAliAppPayMethod extends AbstractChinaumsPayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_ALI_APP.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/trade/precreate";
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
