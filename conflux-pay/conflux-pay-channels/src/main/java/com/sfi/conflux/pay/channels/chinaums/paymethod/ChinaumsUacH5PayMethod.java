package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.pay.channel.PayMethod;

/**
 * 天满云闪付 H5支付（GET 跳转）：端点 {@code /v1/netpay/uac/order}，instMid=H5DEFAULT。
 *
 * @author lianghang
 */
public class ChinaumsUacH5PayMethod extends AbstractChinaumsH5PayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_UAC_H5.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/uac/order";
    }

    @Override
    protected String instMid() {
        return "H5DEFAULT";
    }
}
