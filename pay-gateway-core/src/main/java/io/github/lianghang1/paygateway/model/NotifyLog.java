package io.github.lianghang1.paygateway.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 下游通知记录：网关向使用方业务系统 HTTP 通知的投递与重试台账。
 *
 * <p>{@code requestContent} 在入队时固化（订单快照 JSON），重试投递同一内容；
 * {@code notifyTimes} 为已投递次数，{@code nextNotifyTime} 为下次投递时间，
 * {@code completed} 表示重试计划已终结（成功或超出重试上限）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class NotifyLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知记录号 */
    private String notifyId;
    /** 使用方应用标识 */
    private String appId;
    /** 单据类型 */
    private BizOrderType orderType;
    /** 网关单号（支付/退款/转账/分账单号） */
    private String orderId;
    /** 使用方业务单号 */
    private String bizOrderNo;
    /** 通知地址 */
    private String notifyUrl;
    /** 已投递次数 */
    private Integer notifyTimes;
    /** 下次投递时间（completed 后无意义） */
    private LocalDateTime nextNotifyTime;
    /** 最近一次投递是否成功 */
    private Boolean success;
    /** 重试计划是否终结（成功或超出上限） */
    private Boolean completed;
    /** 通知请求体（JSON，入队时固化） */
    private String requestContent;
    /** 最近一次响应摘要（截断存储） */
    private String responseContent;
    /** 创建时间 */
    private LocalDateTime createdTime;
    /** 更新时间 */
    private LocalDateTime updatedTime;
}
