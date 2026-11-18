package io.github.lianghang1.conflux.pay.channels.chinaums;

import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.RefundState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 天满状态与字段翻译：平台状态码 → 网关统一状态、时间串解析。
 *
 * <p>天满 errCode=SUCCESS 表示接口受理成功；业务状态在 status 字段：
 * WAIT_BUYER_PAY / NEW_ORDER=待支付，TRADE_SUCCESS=成功，
 * TRADE_CLOSED=关闭，UNKNOWN=未知。退款状态在 refundStatus：SUCCESS 等。</p>
 *
 * @author lianghang
 */
public final class ChinaumsStateTranslator {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ChinaumsStateTranslator() {
    }

    /** 平台成功码。 */
    public static boolean isSuccess(String errCode) {
        return "SUCCESS".equals(errCode);
    }

    /** 交易状态 → 支付状态。 */
    public static PaymentState toPaymentState(String status) {
        if (status == null) {
            return PaymentState.PAYING;
        }
        switch (status) {
            case "TRADE_SUCCESS":
                return PaymentState.SUCCEEDED;
            case "TRADE_CLOSED":
                return PaymentState.CLOSED;
            case "WAIT_BUYER_PAY":
            case "NEW_ORDER":
                return PaymentState.PAYING;
            case "UNKNOWN":
                return PaymentState.FAILED;
            default:
                return PaymentState.PAYING;
        }
    }

    /** 退款状态 → 退款状态（refundStatus）。 */
    public static RefundState toRefundState(String refundStatus, String status) {
        if ("SUCCESS".equals(refundStatus)) {
            return RefundState.SUCCEEDED;
        }
        if ("FAILED".equals(refundStatus)) {
            return RefundState.FAILED;
        }
        // 部分渠道退款受理阶段只给 status=TRADE_SUCCESS 而 refundStatus 缺省，按处理中
        return RefundState.PROCESSING;
    }

    /** 解析天满时间串 yyyy-MM-dd HH:mm:ss。 */
    public static LocalDateTime toLocalDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
