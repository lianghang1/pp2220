package com.sfi.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 发票交付结果。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class DeliveryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道是否已受理交付请求。 */
    private boolean accepted;
    /** 是否异步受理（真实送达结果走回调）。 */
    private boolean asyncAccepted;
    private String channelOrderNo;
    private String errorCode;
    private String errorMsg;
    /** 渠道原始报文。 */
    private String rawPayload;

    public static DeliveryResult ok() {
        return new DeliveryResult().setAccepted(true);
    }

    public static DeliveryResult acceptedAsync(String channelOrderNo) {
        return new DeliveryResult().setAccepted(true).setAsyncAccepted(true)
                .setChannelOrderNo(channelOrderNo);
    }

    public static DeliveryResult failed(String errorCode, String errorMsg) {
        return new DeliveryResult().setAccepted(false)
                .setErrorCode(errorCode).setErrorMsg(errorMsg);
    }
}
