package io.github.lianghang1.conflux.pay.channels.alipay;

import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.ProfitShareState;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.pay.model.TransferState;
import io.github.lianghang1.conflux.common.util.AmountUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付宝侧状态与金额到网关统一模型的翻译器。
 *
 * <p>支付宝交易状态：TRADE_SUCCESS/TRADE_FINISHED 成功、WAIT_BUYER_PAY 支付中、
 * TRADE_CLOSED 已关闭。转账状态：SUCCESS 成功、FAIL 失败、其余（DEALING 等）处理中。
 * 支付宝金额一律为元（两位小数字符串），框架内部统一为分。</p>
 *
 * @author lianghang
 */
public final class AlipayStateTranslator {

    private AlipayStateTranslator() {
    }

    /** 支付宝交易状态 → 网关支付状态。 */
    public static PaymentState toPaymentState(String tradeStatus) {
        if (tradeStatus == null) {
            return PaymentState.PAYING;
        }
        switch (tradeStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return PaymentState.SUCCEEDED;
            case "TRADE_CLOSED":
                return PaymentState.CLOSED;
            case "WAIT_BUYER_PAY":
            default:
                return PaymentState.PAYING;
        }
    }

    /** 支付宝转账状态 → 网关转账状态。 */
    public static TransferState toTransferState(String status) {
        if (status == null) {
            return TransferState.PROCESSING;
        }
        switch (status) {
            case "SUCCESS":
                return TransferState.SUCCEEDED;
            case "FAIL":
                return TransferState.FAILED;
            default:
                return TransferState.PROCESSING;
        }
    }

    /** 支付宝退款（同步受理）→ 网关退款状态；fundChange=Y 即成功。 */
    public static RefundState toRefundState(String fundChange) {
        return "Y".equals(fundChange) ? RefundState.SUCCEEDED : RefundState.PROCESSING;
    }

    /** 元字符串 → 分；null/空白返回 null。 */
    public static Long toFen(String yuan) {
        if (yuan == null || yuan.isEmpty()) {
            return null;
        }
        return AmountUtil.toFen(new BigDecimal(yuan));
    }

    /** 分 → 元字符串（两位小数）。 */
    public static String toYuan(Long fen) {
        return fen == null ? null : AmountUtil.fromFen(fen).toPlainString();
    }

    /** SDK Date 字段 → 本地时间；null 返回 null。 */
    public static LocalDateTime toLocalDateTime(java.util.Date date) {
        return date == null ? null
                : java.time.LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
    }
}
