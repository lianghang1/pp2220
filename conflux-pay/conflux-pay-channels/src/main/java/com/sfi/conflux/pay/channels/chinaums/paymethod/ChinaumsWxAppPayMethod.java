package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.pay.channel.PayMethod;

/**
 * 天满微信 APP支付：端点 {@code /v1/netpay/wx/unified-order}，instMid=APPDEFAULT，支付要素取 {@code appPayRequest}。
 *
 * @author lianghang
 */
public class ChinaumsWxAppPayMethod extends AbstractChinaumsPayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_WX_APP.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/wx/unified-order";
    }

    @Override
    protected String instMid() {
        return "APPDEFAULT";
    }

    @Override
    protected String tradeType() {
        return "APP";
    }

    @Override
    protected boolean needSubAppId() {
        return true;
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
