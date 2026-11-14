package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.pay.model.BizOrderType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 下游通知报文：网关向使用方业务系统 HTTP 通知的请求体（JSON）。
 *
 * <p>报文在通知入队时按订单快照固化，重试投递同一内容；
 * 接收方如需防篡改校验，可在 starter 中配置通知密钥开启 HMAC 签名头。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class NotifyMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String appId;
    /** 单据类型 */
    private BizOrderType orderType;
    /** 网关单号 */
    private String orderId;
    /** 使用方业务单号 */
    private String bizOrderNo;
    /** 单据状态（终态语义：SUCCEEDED/FAILED/CLOSED/FINISHED 等） */
    private String state;
    /** 金额（分） */
    private Long amount;
    /** 币种 */
    private String currency;
    /** 渠道编码 */
    private String channelCode;
    /** 渠道侧单号（可空） */
    private String channelOrderNo;
    /** 成功/完成时间（可空） */
    private LocalDateTime succeedTime;
    /** 错误码（可空） */
    private String errorCode;
    /** 错误信息（可空） */
    private String errorMsg;
}
