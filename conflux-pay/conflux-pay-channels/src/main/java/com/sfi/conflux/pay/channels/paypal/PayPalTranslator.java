package com.sfi.conflux.pay.channels.paypal;

import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.pay.model.RefundState;
import com.sfi.conflux.pay.model.TransferState;
import com.sfi.conflux.common.util.AmountUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PayPal 侧状态、金额与链接到网关统一模型的翻译器。
 *
 * <p>订单状态：COMPLETED 成功、VOIDED 已关闭、其余处理中；
 * 退款状态：COMPLETED 成功、其余处理中；Payouts 批次：SUCCESS 成功、
 * FAILED/DENIED/CANCELED 失败、其余处理中。金额为币种字符串（两位小数），
 * 框架内部统一为分。</p>
 *
 * @author lianghang
 */
public final class PayPalTranslator {

    private PayPalTranslator() {
    }

    /** PayPal 订单状态 → 网关支付状态。 */
    public static PaymentState toPaymentState(String orderStatus) {
        if (orderStatus == null) {
            return PaymentState.PAYING;
        }
        switch (orderStatus) {
            case "COMPLETED":
                return PaymentState.SUCCEEDED;
            case "VOIDED":
                return PaymentState.CLOSED;
            case "CREATED":
            case "SAVED":
            case "APPROVED":
            default:
                return PaymentState.PAYING;
        }
    }

    /** PayPal 退款状态 → 网关退款状态。 */
    public static RefundState toRefundState(String refundStatus) {
        return "COMPLETED".equals(refundStatus) ? RefundState.SUCCEEDED : RefundState.PROCESSING;
    }

    /** PayPal Payouts 批次状态 → 网关转账状态。 */
    public static TransferState toTransferState(String batchStatus) {
        if (batchStatus == null) {
            return TransferState.PROCESSING;
        }
        switch (batchStatus) {
            case "SUCCESS":
                return TransferState.SUCCEEDED;
            case "FAILED":
            case "DENIED":
            case "CANCELED":
                return TransferState.FAILED;
            default:
                return TransferState.PROCESSING;
        }
    }

    /** 分 → PayPal 金额字符串（两位小数）。 */
    public static String toYuan(Long fen) {
        return fen == null ? null : AmountUtil.fromFen(fen).toPlainString();
    }

    /** PayPal 金额字符串 → 分；null/空白返回 null。 */
    public static Long toFen(String yuan) {
        if (yuan == null || yuan.isEmpty()) {
            return null;
        }
        return AmountUtil.toFen(new BigDecimal(yuan));
    }

    /** 取响应文本字段。 */
    public static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 取订单响应中的指定 rel 链接（approve 等）。 */
    public static String link(Map<String, Object> order, String rel) {
        Object links = order.get("links");
        if (!(links instanceof List)) {
            return null;
        }
        for (Object item : (List<?>) links) {
            if (item instanceof Map) {
                Map<?, ?> link = (Map<?, ?>) item;
                if (rel.equals(link.get("rel"))) {
                    Object href = link.get("href");
                    return href == null ? null : String.valueOf(href);
                }
            }
        }
        return null;
    }

    /** 从订单响应提取捕获单 ID（purchase_units → payments → captures[0].id）。 */
    public static String captureId(Map<String, Object> order) {
        Map<String, Object> capture = firstCapture(order);
        return capture == null ? null : text(capture.get("id"));
    }

    /** 从订单响应提取捕获金额（分）。 */
    public static Long captureAmount(Map<String, Object> order) {
        Map<String, Object> capture = firstCapture(order);
        if (capture == null) {
            return null;
        }
        Object amount = capture.get("amount");
        if (amount instanceof Map) {
            return toFen(((Map<?, ?>) amount).get("value") == null
                    ? null : String.valueOf(((Map<?, ?>) amount).get("value")));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCapture(Map<String, Object> order) {
        Object units = order.get("purchase_units");
        if (!(units instanceof List) || ((List<?>) units).isEmpty()) {
            return null;
        }
        Object unit = ((List<?>) units).get(0);
        if (!(unit instanceof Map)) {
            return null;
        }
        Object payments = ((Map<String, Object>) unit).get("payments");
        if (!(payments instanceof Map)) {
            return null;
        }
        Object captures = ((Map<String, Object>) payments).get("captures");
        if (!(captures instanceof List) || ((List<?>) captures).isEmpty()) {
            return null;
        }
        Object capture = ((List<?>) captures).get(0);
        return capture instanceof Map ? (Map<String, Object>) capture : null;
    }
}
