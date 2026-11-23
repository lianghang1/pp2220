package com.sfi.conflux.pay.channels.wechat;

import com.google.gson.annotations.SerializedName;
import com.github.binarywang.wxpay.bean.notify.WxPayBaseNotifyV3Result;
import lombok.Data;

/**
 * 微信分账结果通知（V3 JSON 通知）的解密模型：随通用通知解析
 * （baseParseOrderNotifyV3Result）还原，字段名与微信报文一致。
 *
 * @author lianghang
 */
@Data
public class WechatProfitShareNotifyV3Result implements WxPayBaseNotifyV3Result<WechatProfitShareNotifyV3Result.DecryptNotifyResult> {

    private com.github.binarywang.wxpay.bean.notify.OriginNotifyResponse rawData;
    private DecryptNotifyResult result;

    @Override
    public void setRawData(com.github.binarywang.wxpay.bean.notify.OriginNotifyResponse rawData) {
        this.rawData = rawData;
    }

    @Override
    public void setResult(DecryptNotifyResult data) {
        this.result = data;
    }

    /** 解密后的分账结果。 */
    @Data
    public static class DecryptNotifyResult implements java.io.Serializable {

        /** 服务商商户号 */
        @SerializedName(value = "sp_mchid")
        private String spMchId;

        /** 特约商户号 */
        @SerializedName(value = "sub_mchid")
        private String subMchId;

        /** 商户分账单号（网关分账单号） */
        @SerializedName(value = "out_order_no")
        private String outOrderNo;

        /** 微信分账单号 */
        @SerializedName(value = "order_id")
        private String orderId;

        /** 分账单状态：PROCESSING / FINISHED */
        @SerializedName(value = "state")
        private String state;

        /** 分账完成时间 */
        @SerializedName(value = "finish_time")
        private String finishTime;
    }
}
