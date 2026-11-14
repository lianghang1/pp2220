package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 授权凭证提交：使用方把用户完成的动作结果交回框架。
 *
 * <p>不同 {@code AuthType} 使用不同字段：SMS 用 {@code captcha}，
 * QR/FACE 用 {@code token}；其余渠道自定义字段放 {@code extra}。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class AuthSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appId;
    /** 授权会话标识（必填）。 */
    private String sessionId;
    /** 短信验证码。 */
    private String captcha;
    /** 扫码/扫脸回传 token。 */
    private String token;
    /** 渠道自定义凭证。 */
    private Map<String, String> extra;
}
