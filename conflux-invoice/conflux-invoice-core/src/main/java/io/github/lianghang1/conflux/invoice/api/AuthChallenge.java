package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.invoice.model.AuthType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 授权挑战：渠道要求人工完成扫脸/短信/扫码时返回给使用方的信息。
 *
 * <p>使用方拿到 {@link #sessionId} 后引导用户，再通过
 * {@link AuthSubmission} 提交凭证。{@link #expireTime} 之后会话失效，
 * 必须重新发起授权。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class AuthChallenge implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private AuthType authType;
    /** 扫码/扫脸跳转地址。 */
    private String challengeUrl;
    /** 短信提示（如「验证码已发送至 138****1234」）。 */
    private String challengeText;
    private LocalDateTime expireTime;
    /** 渠道附加信息（二维码 ticket、脱敏手机号等）。 */
    private Map<String, String> extra;
}
