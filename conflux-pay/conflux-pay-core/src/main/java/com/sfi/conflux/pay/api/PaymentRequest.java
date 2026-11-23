package com.sfi.conflux.pay.api;

import com.sfi.conflux.common.exception.CommonErrorCode;
import com.sfi.conflux.common.exception.GatewayAssert;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.util.StringUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一下单请求。
 *
 * <p>SDK 无台账：{@code bizOrderNo} 作为渠道商户单号透传，重复下单由渠道侧以该单号
 * 幂等去重，SDK 不查重、不返回既有订单。使用方需自行保证同一业务单号的参数一致。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PaymentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String configId;
    /** 使用方业务单号（configId 内唯一，必填） */
    private String bizOrderNo;
    /** 支付方式编码（必填，见 PayMethod 内置常量或自定义注册） */
    private String method;
    /** 渠道编码（选填；缺省取支付方式的默认渠道，用于同方式多渠道接入） */
    private String channelCode;
    /** 支付金额（分，必填，大于 0） */
    private Long amount;
    /** 币种（选填，默认 CNY） */
    private String currency;
    /** 商品标题 */
    private String subject;
    /** 商品描述 */
    private String body;
    /** 下单客户端 IP */
    private String clientIp;
    /**
     * 异步通知地址（选填，按单覆盖）：非空时覆盖渠道配置中的 notifyUrl，实现同一渠道
     * 按订单动态指定回调地址；留空则回退到配置的 notifyUrl。
     */
    private String notifyUrl;
    /** 渠道附加参数（选填，如 JSAPI 方式的 openid；key 约定见各渠道文档） */
    private Map<String, String> channelExtras;

    /** 参数校验：不满足抛 {@link GatewayErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(configId, CommonErrorCode.BAD_REQUEST, "configId 不能为空");
        GatewayAssert.notBlank(bizOrderNo, CommonErrorCode.BAD_REQUEST, "bizOrderNo 不能为空");
        GatewayAssert.notBlank(method, CommonErrorCode.BAD_REQUEST, "method 不能为空");
        GatewayAssert.notNull(amount, CommonErrorCode.BAD_REQUEST, "amount 不能为空");
        GatewayAssert.isTrue(amount > 0, CommonErrorCode.BAD_REQUEST, "amount 必须大于 0");
        if (StringUtils.isBlank(currency)) {
            currency = "CNY";
        }
    }
}
