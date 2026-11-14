package io.github.lianghang1.conflux.invoice.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 授权会话：数电票渠道「登录态/扫脸/短信」的显式建模。
 *
 * <p>会话有 {@link #expireTime}，过期后必须由使用方重新发起授权；框架不自动
 * 静默续期，避免用过期的税局登录态开票。{@link #payload} 承载渠道自定义会话凭据
 * （如渠道 sessionId、二维码 ticket），框架零感知。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class AuthSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String invoiceId;
    private String appId;
    private String channelCode;
    private String accountKey;
    private AuthType authType;
    private AuthSessionState state;
    /** 扫码/扫脸跳转地址。 */
    private String challengeUrl;
    /** 短信提示文案/手机号脱敏串。 */
    private String challengeText;
    /** 会话过期时间。 */
    private LocalDateTime expireTime;
    private LocalDateTime submittedTime;
    /** 渠道自定义会话数据。 */
    private Map<String, String> payload;

    private long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    /** 是否已过期（以给定时间为准）。 */
    public boolean isExpired(LocalDateTime now) {
        return expireTime != null && now != null && !now.isBefore(expireTime);
    }

    /** 是否仍可提交凭证。 */
    public boolean canSubmit(LocalDateTime now) {
        return state == AuthSessionState.PENDING && !isExpired(now);
    }
}
