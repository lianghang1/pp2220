package io.github.lianghang1.conflux.pay.api;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.util.StringUtils;
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
    /** 渠道附加参数（选填，如 JSAPI 方式的 openid；key 约定见各渠道文档） */
    private Map<String, String> channelExtras;

    /** 参数校验：不满足抛 {@link GatewayErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, CommonErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizOrderNo, CommonErrorCode.BAD_REQUEST, "bizOrderNo 不能为空");
        GatewayAssert.notBlank(method, CommonErrorCode.BAD_REQUEST, "method 不能为空");
        GatewayAssert.notNull(amount, CommonErrorCode.BAD_REQUEST, "amount 不能为空");
        GatewayAssert.isTrue(amount > 0, CommonErrorCode.BAD_REQUEST, "amount 必须大于 0");
        if (StringUtils.isBlank(currency)) {
            currency = "CNY";
        }
    }
}
