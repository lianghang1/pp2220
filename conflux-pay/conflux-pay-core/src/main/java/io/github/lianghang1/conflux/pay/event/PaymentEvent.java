package io.github.lianghang1.conflux.pay.event;

import io.github.lianghang1.conflux.pay.model.BizOrderType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单事件：订单状态迁移后的统一通知载体。
 *
 * <p>{@code subject} 为引发事件的领域记录本体（Payment/Refund/Transfer/ProfitShare），
 * 监听方按 {@code orderType} 向下转型；监听异常被引擎隔离记录，不影响主流程。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PaymentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型 */
    private EventType type;
    /** 使用方应用标识 */
    private String appId;
    /** 单据类型 */
    private BizOrderType orderType;
    /** 网关单号 */
    private String orderId;
    /** 使用方业务单号 */
    private String bizOrderNo;
    /** 事件发生时间 */
    private LocalDateTime occurredAt;
    /** 领域记录本体（按 orderType 转型使用） */
    private transient Object subject;

    /**
     * 事件类型枚举：覆盖四类单据的关键状态迁移。
     */
    public enum EventType {
        /** 支付单已创建 */
        PAYMENT_CREATED,
        /** 支付单进入待支付（已取得渠道唤起参数） */
        PAYMENT_PAYING,
        /** 支付成功 */
        PAYMENT_SUCCEEDED,
        /** 支付失败 */
        PAYMENT_FAILED,
        /** 支付单已关闭 */
        PAYMENT_CLOSED,
        /** 退款单已创建 */
        REFUND_CREATED,
        /** 退款成功 */
        REFUND_SUCCEEDED,
        /** 退款失败 */
        REFUND_FAILED,
        /** 转账单已创建 */
        TRANSFER_CREATED,
        /** 转账成功 */
        TRANSFER_SUCCEEDED,
        /** 转账失败 */
        TRANSFER_FAILED,
        /** 分账单已创建 */
        PROFIT_SHARE_CREATED,
        /** 分账完成（含失败） */
        PROFIT_SHARE_FINISHED
    }
}
