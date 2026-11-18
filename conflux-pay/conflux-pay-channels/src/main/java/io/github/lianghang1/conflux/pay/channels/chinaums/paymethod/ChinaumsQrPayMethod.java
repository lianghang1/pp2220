package io.github.lianghang1.conflux.pay.channels.chinaums.paymethod;

import io.github.lianghang1.conflux.pay.channel.PayMethod;

/**
 * 天满C扫B 主扫支付：端点 {@code /v1/netpay/bills/get-qrcode}，instMid=QRPAYDEFAULT，支付要素取 {@code billQRCode}。
 *
 * @author lianghang
 */
public class ChinaumsQrPayMethod extends AbstractChinaumsPayMethod {

    @Override
    public String method() {
        return PayMethod.CHINAUMS_QR.getCode();
    }

    @Override
    protected String endpoint() {
        return "/v1/netpay/bills/get-qrcode";
    }

    @Override
    protected String instMid() {
        return "QRPAYDEFAULT";
    }

    @Override
    protected String payloadField() {
        return "billQRCode";
    }

    @Override
    protected PayloadKind payloadKind() {
        return PayloadKind.QRCODE;
    }
}
