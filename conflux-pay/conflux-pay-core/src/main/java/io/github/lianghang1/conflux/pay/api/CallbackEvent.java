package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.pay.model.BizOrderType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 渠道回调事件：渠道回调处理器验签、解析后的归一化结果。
 *
 * <p>{@code bizOrderNo} 为渠道侧回传的业务单号（支付=业务单号、退款=业务退款单号、
 * 转账=业务转账单号），使用方据此定位自己的订单；
 * {@code success} 为归一化结果标记，渠道原始字段保留在 {@code raw}。
 * SDK 只验签归一、不查重、不定位台账。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class CallbackEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回调单据类型 */
    private BizOrderType type;
    /** 使用方应用标识（经渠道侧应用标识换算得到） */
    private String appId;
    /** 渠道侧回传的业务单号（使用方据此定位自己的订单并查重） */
    private String bizOrderNo;
    /** 渠道侧单号 */
    private String channelOrderNo;
    /** 归一化结果标记：true=成功，false=失败/关闭 */
    private boolean success;
    /** 渠道侧金额（分，渠道未返回时为 null） */
    private Long amount;
    /** 业务发生时间（渠道侧时间，可空） */
    private LocalDateTime occurTime;
    /** 渠道原始字段（留痕与扩展用） */
    private Map<String, String> raw;
}
