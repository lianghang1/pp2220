package io.github.lianghang1.paygateway.api;

import io.github.lianghang1.paygateway.exception.GatewayAssert;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 转账请求：渠道账户向收款方转账。
 *
 * <p>{@code method} 决定转账渠道与方式（如 WECHAT_TRANS 商家转账到零钱、
 * ALI_TRANSFER 转账到支付宝账户）；收款方账户与姓名的渠道语义见各渠道文档。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class TransferRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String appId;
    /** 使用方业务转账单号（appId 内唯一，必填） */
    private String bizTransferNo;
    /** 转账方式编码（必填） */
    private String method;
    /** 收款方账户（必填，渠道语义各异） */
    private String payeeAccount;
    /** 收款方姓名（选填；部分渠道用于实名校验） */
    private String payeeName;
    /** 转账金额（分，必填，大于 0） */
    private Long amount;
    /** 币种（选填，默认 CNY） */
    private String currency;
    /** 转账备注/附言 */
    private String remark;
    /** 下游通知地址（选填；转账终态后通知） */
    private String notifyUrl;
    /** 渠道附加参数（选填） */
    private Map<String, String> channelExtras;

    /** 参数校验：不满足抛 {@link GatewayErrorCode#BAD_REQUEST}。 */
    public void check() {
        GatewayAssert.notBlank(appId, GatewayErrorCode.BAD_REQUEST, "appId 不能为空");
        GatewayAssert.notBlank(bizTransferNo, GatewayErrorCode.BAD_REQUEST, "bizTransferNo 不能为空");
        GatewayAssert.notBlank(method, GatewayErrorCode.BAD_REQUEST, "method 不能为空");
        GatewayAssert.notBlank(payeeAccount, GatewayErrorCode.BAD_REQUEST, "payeeAccount 不能为空");
        GatewayAssert.notNull(amount, GatewayErrorCode.BAD_REQUEST, "amount 不能为空");
        GatewayAssert.isTrue(amount > 0, GatewayErrorCode.BAD_REQUEST, "amount 必须大于 0");
        if (currency == null || currency.isEmpty()) {
            currency = "CNY";
        }
    }
}
