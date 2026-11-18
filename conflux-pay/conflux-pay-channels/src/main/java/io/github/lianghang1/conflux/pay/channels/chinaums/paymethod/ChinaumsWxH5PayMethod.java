package io.github.lianghang1.conflux.pay.channels.chinaums.paymethod;

import io.github.lianghang1.conflux.pay.channel.PayMethod;

/**
 * 天满微信 H5支付（GET 跳转）：端点 {@code /v1/netpay/wxpay/h5-pay}，instMid=H5DEFAULT。
 *
 * @author lianghang
 */
public class ChinaumsWxH5PayMethod extends AbstractChinaumsH5PayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_WX_H5.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/wxpay/h5-pay";
    }

    @Override
    protected String instMid() {
        return "H5DEFAULT";
    }

    @Override
    protected boolean needSubAppId() {
        return true;
    }
}
