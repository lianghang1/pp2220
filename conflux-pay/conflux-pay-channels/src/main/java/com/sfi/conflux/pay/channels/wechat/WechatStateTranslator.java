package com.sfi.conflux.pay.channels.wechat;

import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.pay.model.ProfitShareState;
import com.sfi.conflux.pay.model.RefundState;
import com.sfi.conflux.pay.model.TransferState;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;

/**
 * 微信侧状态与时间到网关统一模型的翻译器。
 *
 * <p>微信支付单状态（trade_state）：SUCCESS/REFUND 视为支付成功；
 * CLOSED/REVOKED 视为已关闭；NOTPAY/USERPAYING/ACCEPT 视为支付中；
 * PAYERROR 视为失败。退款状态（refund_status）：SUCCESS 成功、CLOSED 关闭、
 * ABNORMAL 异常（按失败处理）、PROCESSING 处理中。</p>
 *
 * @author lianghang
 */
@Slf4j
public final class WechatStateTranslator {

    private WechatStateTranslator() {
    }

    /** 微信交易状态 → 网关支付状态。 */
    public static PaymentState toPaymentState(String tradeState) {
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

    /** 微信退款状态 → 网关退款状态；null（未返回状态）按处理中。 */
    public static RefundState toRefundState(String refundStatus) {
        if (refundStatus == null) {
            return RefundState.PROCESSING;
        }
        switch (refundStatus) {
            case "SUCCESS":
                return RefundState.SUCCEEDED;
            case "CLOSED":
                return RefundState.CLOSED;
            case "ABNORMAL":
                return RefundState.FAILED;
            case "PROCESSING":
            default:
                return RefundState.PROCESSING;
        }
    }

    /** 微信转账批次/明细状态 → 网关转账状态。 */
    public static TransferState toTransferState(String batchOrDetailStatus) {
        if (batchOrDetailStatus == null) {
            return TransferState.PROCESSING;
        }
        switch (batchOrDetailStatus) {
            case "SUCCESS":
            case "FINISH":
                return TransferState.SUCCEEDED;
            case "FAIL":
                return TransferState.FAILED;
            default:
                return TransferState.PROCESSING;
        }
    }

    /** 微信分账单状态 → 网关分账状态。 */
    public static ProfitShareState toProfitShareState(String orderState) {
        if (orderState == null) {
            return ProfitShareState.PROCESSING;
        }
        if ("FINISHED".equals(orderState)) {
            return ProfitShareState.FINISHED;
        }
        if ("CLOSED".equals(orderState)) {
            return ProfitShareState.FAILED;
        }
        return ProfitShareState.PROCESSING;
    }

    /** V2 时间格式（{@code yyyyMMddHHmmss}，如 {@code 20150806125346}）。 */
    private static final DateTimeFormatter V2_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 微信时间 → 本地时间：兼容 V2（{@code yyyyMMddHHmmss}）与 V3 ISO8601
     * （如 {@code 2020-01-01T10:00:00+08:00}）；解析失败返回 null。
     */
    public static LocalDateTime toLocalDateTime(String time) {
        if (time == null || time.isEmpty()) {
            return null;
        }
        if (time.length() == 14 && time.matches("\\d{14}")) {
            try {
                return LocalDateTime.parse(time, V2_TIME);
            } catch (Exception e) {
                log.debug("微信 V2 时间解析失败，按 null 处理，原值: {}", time, e);
                return null;
            }
        }
        try {
            return OffsetDateTime.parse(time, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception e) {
            log.debug("微信 ISO8601 时间解析失败，按 null 处理，原值: {}", time, e);
            return null;
        }
    }

    /** 网关订单过期时间 → 微信 RFC3339 格式（{@code yyyy-MM-dd'T'HH:mm:ssXXX}）。 */
    public static String toWechatTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atOffset(OffsetDateTime.now().getOffset())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }
}
