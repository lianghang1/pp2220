package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.invoice.model.AuthSession;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 授权凭证提交：使用方把用户完成的动作结果交回 SDK，SDK 透传渠道。
 *
 * <p>SDK 无台账——授权会话上下文由使用方随 {@code session} 原样带回（开票返回
 * WAITING_AUTH 时的 AuthChallenge 已携带不透明上下文），不暂存会话。
 * 不同 {@code AuthType} 使用不同字段：SMS 用 {@code captcha}，QR/FACE 用 {@code token}。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class AuthSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String appId;
    /** 短信验证码 */
    private String captcha;
    /** 扫码/扫脸回传 token */
    private String token;
    /** 渠道自定义凭证 */
    private Map<String, String> extra;
    /** 授权会话载体（必填，含会话标识与不透明上下文） */
    private AuthSession session;
    /** 关联发票载体（必填，授权通过后重新发起开票用） */
    private Invoice invoice;

    /** 参数校验。 */
    public void check() {
        GatewayAssert.param(appId != null && appId.trim().length() > 0, "appId 不能为空");
        GatewayAssert.param(session != null, "session 授权会话载体不能为空");
        GatewayAssert.param(invoice != null, "invoice 发票载体不能为空");
    }
}
