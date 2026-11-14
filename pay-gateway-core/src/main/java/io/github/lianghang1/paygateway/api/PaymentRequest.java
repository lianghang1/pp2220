package io.github.lianghang1.paygateway.api;

import io.github.lianghang1.paygateway.exception.GatewayAssert;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一下单请求。
 *
 * <p>幂等约定：同一 {@code appId + bizOrderNo} 重复下单时，若订单仍在创建态且
 * 未取得渠道唤起参数，则视为重试（重新发起渠道调用）；否则返回既有订单。
 * 参数与既有订单不一致时抛重复订单异常。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PaymentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String appId;
    /** 使用方业务单号（appId 内唯一，必填） */
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
    /** 下游通知地址（选填；支付成功后网关 HTTP 通知该地址） */
    private String notifyUrl;
    /** 支付有效期（秒，选填；由渠道包决定是否使用） */
    private Long expireSeconds;
    /** 渠道附加参数（选填，如 JSAPI 方式的 openid；key 约定见各渠道文档） */
    private Map<String, String> channelExtras;

    /** 参数校验：不满足抛 {@link GatewayErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, GatewayErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizOrderNo, GatewayErrorCode.BAD_REQUEST, "bizOrderNo 不能为空");
        GatewayAssert.notBlank(method, GatewayErrorCode.BAD_REQUEST, "method 不能为空");
        GatewayAssert.notNull(amount, GatewayErrorCode.BAD_REQUEST, "amount 不能为空");
        GatewayAssert.isTrue(amount > 0, GatewayErrorCode.BAD_REQUEST, "amount 必须大于 0");
        if (StringUtils.isBlank(currency)) {
            currency = "CNY";
        }
    }
}
