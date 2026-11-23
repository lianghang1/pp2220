package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.pay.channel.PayMethod;

/**
 * 天满支付宝 小程序支付：端点 {@code /v1/netpay/trade/create}，instMid=MINIDEFAULT，支付要素取 {@code miniPayRequest}。
 *
 * @author lianghang
 */
public class ChinaumsAliMiniPayMethod extends AbstractChinaumsPayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_ALI_MINI.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/trade/create";
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
    protected String payloadField() {
        return "miniPayRequest";
    }

    @Override
    protected PayloadKind payloadKind() {
        return PayloadKind.JSAPI;
    }
}
