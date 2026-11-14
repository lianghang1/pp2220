package io.github.lianghang1.conflux.pay.channels.unionpay;

import io.github.lianghang1.conflux.pay.model.PaymentState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 云闪付请求组装与状态映射：公共参数、订单类型、应答码到网关状态机的翻译。
 *
 * <p>订单类型：upJs（银联二维码跳转）、upBar（银联付款码）。
 * 查单原应答码（origRespCode）："00" 成功、"02"/"05" 处理中。</p>
 *
 * @author lianghang
 */
public final class UnionPayTranslator {

    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private UnionPayTranslator() {
    }

    /** 公共参数：订单号、下单时间、金额（分）、币种、订单描述、商户号。 */
    public static void fillPublicParams(Map<String, Object> params, String orderNo, Long amount,
                                        String subject, String merchantId, String serProvId) {
        params.put("orderNo", orderNo);
        params.put("orderTime", LocalDateTime.now().format(ORDER_TIME));
        params.put("txnAmt", amount);
        params.put("currencyCode", "156");
        params.put("orderInfo", subject == null ? "conflux-pay" : subject);
        if (merchantId != null) {
            params.put("merId", merchantId);
        }
        if (serProvId != null) {
            params.put("serProvId", serProvId);
        }
    }

    /** 订单时间格式（yyyyMMddHHmmss）。 */
    public static String orderTime(LocalDateTime time) {
        return time.format(ORDER_TIME);
    }

    /** 下单应答码 → 网关支付状态（"00" 受理成功进入 PAYING，由 payData 唤起）。 */
    public static PaymentState toPaymentState(String respCode) {
        return "00".equals(respCode) ? PaymentState.PAYING : PaymentState.FAILED;
    }

    /** 查单原应答码（origRespCode）→ 网关支付状态。 */
    public static PaymentState fromOrigRespCode(String origRespCode) {
        if ("00".equals(origRespCode)) {
            return PaymentState.SUCCEEDED;
        }
        if ("02".equals(origRespCode) || "05".equals(origRespCode)) {
            return PaymentState.PAYING;
        }
        return PaymentState.FAILED;
    }
}
