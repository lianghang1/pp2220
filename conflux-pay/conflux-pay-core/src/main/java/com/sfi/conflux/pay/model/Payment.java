package com.sfi.conflux.pay.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付载体：承载渠道交互所需的支付要素，随结果返回、随请求回传。
 *
 * <p>金额 {@code amount} 统一为「分」（Long）；{@code payPayload} 为 {@link PayPayload}
 * 的 JSON 序列化内容（供使用方重复获取唤起参数）；{@code extParams} 为下单扩展参数
 * 的 JSON。SDK 无台账，本类只作数据载体，不做任何存储假设。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识 */
    private String configId;
    /** 使用方业务单号（渠道商户单号；查重由使用方负责） */
    private String bizOrderNo;
    /** 支付方式编码（PayMethod.code） */
    private String method;
    /** 渠道编码（ChannelCode 常量或自定义） */
    private String channelCode;
    /** 支付金额（分） */
    private Long amount;
    /** 币种（ISO 4217，如 CNY） */
    private String currency;
    /** 商品标题 */
    private String subject;
    /** 商品描述 */
    private String body;
    /** 下单客户端 IP */
    private String clientIp;
    /** 异步通知地址（按单覆盖渠道配置）；请求优先、配置兜底，由渠道 handler 读取 */
    private String notifyUrl;
    /** 支付单状态 */
    private PaymentState state;
    /** 支付唤起参数（PayPayload 的 JSON，可空） */
    private String payPayload;
    /** 渠道侧单号 */
    private String channelOrderNo;
    /** 渠道/网关错误码（失败时记录） */
    private String errorCode;
    /** 错误信息（失败时记录） */
    private String errorMsg;
    /** 支付成功时间 */
    private LocalDateTime succeedTime;
    /** 下单扩展参数（JSON） */
    private String extParams;
}
