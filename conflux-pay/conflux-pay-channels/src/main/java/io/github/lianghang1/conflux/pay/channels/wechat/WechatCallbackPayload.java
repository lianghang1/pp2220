package io.github.lianghang1.conflux.pay.channels.wechat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 微信回调 resource 解密后的统一报文模型：覆盖支付/退款/转账明细/分账四类通知字段，
 * 未知字段忽略。字段类型以回调报文实际结构为准（snake_case 映射）。
 *
 * @author lianghang
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WechatCallbackPayload {

    /** 商户号 */
    @JsonProperty("mchid")
    private String mchid;

    /** 特约商户号（服务商模式） */
    @JsonProperty("sub_mchid")
    private String subMchid;

    // ---- 支付回调 ----
    @JsonProperty("out_trade_no")
    private String outTradeNo;
    @JsonProperty("transaction_id")
    private String transactionId;
    @JsonProperty("trade_state")
    private String tradeState;
    @JsonProperty("success_time")
    private String successTime;
    @JsonProperty("amount")
    private CallbackAmount amount;

    // ---- 退款回调 ----
    @JsonProperty("out_refund_no")
    private String outRefundNo;
    @JsonProperty("refund_id")
    private String refundId;
    @JsonProperty("refund_status")
    private String refundStatus;

    // ---- 转账明细回调 ----
    @JsonProperty("out_batch_no")
    private String outBatchNo;
    @JsonProperty("batch_id")
    private String batchId;
    @JsonProperty("out_detail_no")
    private String outDetailNo;
    @JsonProperty("detail_id")
    private String detailId;
    /** 转账明细/分账单状态（两类回调共用字段名 state） */
    @JsonProperty("state")
    private String state;

    // ---- 分账回调 ----
    @JsonProperty("out_order_no")
    private String outOrderNo;
    @JsonProperty("order_id")
    private String orderId;

    /** 回调金额（分） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallbackAmount {

        @JsonProperty("total")
        private Long total;

        @JsonProperty("payer_total")
        private Long payerTotal;

        @JsonProperty("refund")
        private Long refund;
    }
}
