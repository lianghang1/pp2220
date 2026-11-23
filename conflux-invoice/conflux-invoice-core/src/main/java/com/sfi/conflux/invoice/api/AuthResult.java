package com.sfi.conflux.invoice.api;

import com.sfi.conflux.invoice.model.InvoiceState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道授权结果：由 {@code AuthSessionHandler} 返回，编排器据此迁移会话与单据状态。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class AuthResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权是否通过。 */
    private boolean verified;
    /** 未通过时是否仍需继续等待（true=保持 WAITING_AUTH，false=置 FAILED）。 */
    private boolean retryable;
    /** 授权通过后单据应进入的状态（通常为 PROCESSING/SUBMITTING）。 */
    private InvoiceState nextState;
    /** 需要（重新）展示给使用方的挑战。 */
    private AuthChallenge challenge;
    private String errorCode;
    private String errorMsg;
    /** 渠道原始报文。 */
    private String rawPayload;

    public static AuthResult verified(InvoiceState nextState) {
        return new AuthResult().setVerified(true).setNextState(nextState);
    }

    public static AuthResult pending(AuthChallenge challenge) {
        return new AuthResult().setVerified(false).setRetryable(true).setChallenge(challenge);
    }

    public static AuthResult rejected(String errorCode, String errorMsg) {
        return new AuthResult().setVerified(false).setRetryable(false)
                .setErrorCode(errorCode).setErrorMsg(errorMsg);
    }
}
