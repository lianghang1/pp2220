package com.sfi.conflux.pay.channels.chinaums.paymethod;

import com.sfi.conflux.pay.channel.PayMethod;

/**
 * 天满支付宝 H5支付（GET 跳转）：端点 {@code /v1/netpay/trade/h5-pay}，instMid=H5DEFAULT。
 *
 * @author lianghang
 */
public class ChinaumsAliH5PayMethod extends AbstractChinaumsH5PayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_ALI_H5.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/trade/h5-pay";
    }

    @Override
    protected String instMid() {
        return "H5DEFAULT";
    }
}
