package com.sfi.conflux.pay.channels.wechat.paymethod;

import com.sfi.conflux.pay.model.PaymentState;
import lombok.extern.slf4j.Slf4j;
/**
 * 微信支付侧状态与时间的归一化桥接（V2/V3 报文共用）。
 *
 * <p>交易状态：SUCCESS 成功；CLOSED/REVOKED 已关闭；PAYERROR 失败；
 * NOTPAY/USERPAYING/ACCEPT 处理中（ACCEPT=已接收待扣款，非成功）；
 * REFUND 属于退款单维度，支付单维度视为成功。</p>
 *
 * @author lianghang
 */
@Slf4j
final class WechatStateBridge {

    private WechatStateBridge() {
    }

    /** 微信交易状态 → 网关支付状态。 */
    static PaymentState toPaymentState(String tradeState) {
        if (tradeState == null) {
            return PaymentState.PAYING;
        }
        switch (tradeState) {
            case "SUCCESS":
            case "REFUND":
                return PaymentState.SUCCEEDED;
            case "CLOSED":
            case "REVOKED":
                return PaymentState.CLOSED;
            case "PAYERROR":
                return PaymentState.FAILED;
            case "NOTPAY":
            case "USERPAYING":
            case "ACCEPT":
            default:
                return PaymentState.PAYING;
        }
    }

    /** 微信时间字段 → 本地时间（V2 为 {@code yyyyMMddHHmmss}，V3 为 ISO8601）。 */
    static java.time.LocalDateTime toLocalDateTime(String time) {
        if (time == null || time.isEmpty()) {
            return null;
        }
        if (time.length() == 14 && time.matches("\\d{14}")) {
            return java.time.LocalDateTime.parse(time,
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }
        try {
            return java.time.OffsetDateTime.parse(time,
                    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception e) {
            log.debug("微信时间解析失败，按 null 处理，原值: {}", time, e);
            return null;
        }
    }
}
