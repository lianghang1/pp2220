package com.sfi.conflux.invoice.model;

/**
 * 红字确认单状态（两段红冲的第一段）。
 *
 * <p>数电票红冲不是「发起即完成」：需先申请红字确认单，由购方在时限内确认，
 * 确认后才能开具红字发票。本枚举显式表达这条长事务。</p>
 *
 * @author lianghang
 */
public enum RedApplyState {

    /** 已建单，尚未提交渠道。 */
    APPLYING(false),
    /** 挂起：已提交，等待购方确认。 */
    WAITING_CONFIRM(false),
    /** 已确认，可开具红字发票。 */
    CONFIRMED(false),
    /** 购方拒绝。 */
    REJECTED(true),
    /** 超过确认时限。 */
    EXPIRED(true),
    /** 红字发票已开具完成。 */
    ISSUED(true),
    /** 申请失败。 */
    FAILED(true);

    private final boolean terminal;

    RedApplyState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    /** 是否具备开具红字发票的条件。 */
    public boolean canIssueRed() {
        return this == CONFIRMED;
    }
}
