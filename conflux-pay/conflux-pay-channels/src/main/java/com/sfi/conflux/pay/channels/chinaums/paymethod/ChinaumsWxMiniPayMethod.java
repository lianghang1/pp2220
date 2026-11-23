package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.pay.channel.PayMethod;

/**
 * 天满微信 小程序支付：端点 {@code /v1/netpay/wx/unified-order}，instMid=MINIDEFAULT，支付要素取 {@code miniPayRequest}。
 *
 * @author lianghang
 */
public class ChinaumsWxMiniPayMethod extends AbstractChinaumsPayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_WX_MINI.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/wx/unified-order";
    }

    @Override
    protected String instMid() {
        return "MINIDEFAULT";
    }

    @Override
    protected String tradeType() {
        return "MINI";
    }

    @Override
    protected boolean needSubAppId() {
        return true;
    }

    @Override
    protected String payloadField() {
        return "miniPayRequest";
    }

    @Override
    protected PayloadKind payloadKind() {
        return PayloadKind.JSAPI;
    }
}
