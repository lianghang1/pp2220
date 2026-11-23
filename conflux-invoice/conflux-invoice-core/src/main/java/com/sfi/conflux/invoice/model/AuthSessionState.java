package com.sfi.conflux.invoice.model;

/**
 * 授权会话状态。
 *
 * @author lianghang
 */
public enum AuthSessionState {

    /** 已发起，等待用户完成动作。 */
    PENDING(false),
    /** 用户已提交凭证，等待渠道校验。 */
    SUBMITTED(false),
    /** 校验通过，可继续开票。 */
    VERIFIED(true),
    /** 已过期，需重新发起。 */
    EXPIRED(true),
    /** 校验失败，需重新发起。 */
    FAILED(true);

    private final boolean terminal;

    AuthSessionState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
