package io.github.lianghang1.conflux.invoice.model;

/**
 * 发票单据状态。
 *
 * <p>与支付状态机的根本差异：发票链路存在两类「挂起态」——
 * {@link #WAITING_AUTH}（税局/服务商要求扫脸、短信或扫码授权，且会话会过期）与
 * {@link #RED_APPLYING}（红字确认单需购方确认，是跨主体的长事务）。
 * 这两类状态都不是失败，也不是终态，必须能被补单/回调重新唤醒。</p>
 *
 * <p>红冲是两段式：先申请红字确认单（RED_APPLYING），对方确认后（RED_APPLY_CONFIRMED）
 * 才能开具红字发票（RED_SUCCEEDED）；不能像退款那样一次调用完成。</p>
 *
 * @author lianghang
 */
public enum InvoiceState {

    /** 已建单，尚未提交渠道。 */
    CREATED(false, false),
    /** 正在向渠道提交开票请求。 */
    SUBMITTING(false, false),
    /** 挂起：等待人工授权（扫脸/短信/扫码），会话有过期时间。 */
    WAITING_AUTH(false, true),
    /** 渠道已受理，异步处理中（数电票常见 40s+ 未完成转异步）。 */
    PROCESSING(false, false),
    /**
     * 蓝字发票开具成功（渠道返回成功）。
     *
     * <p><b>不是终态</b>：数电票开具成功后仍可发起红冲，状态会继续迁移到
     * {@link #RED_APPLYING}；真正不可变的是 {@link #FAILED} / {@link #CLOSED} /
     * {@link #RED_SUCCEEDED}。这也是发票与支付状态机的一处关键差异。</p>
     */
    SUCCEEDED(false, false),
    /** 开票失败（终态，可凭新业务单号重试）。 */
    FAILED(true, false),
    /** 单据作废/关闭（终态）。 */
    CLOSED(true, false),
    /** 挂起：红字确认单已申请，等待购方确认。 */
    RED_APPLYING(false, true),
    /** 红字确认单已确认，具备开具红字发票的条件。 */
    RED_APPLY_CONFIRMED(false, false),
    /** 红字发票开具完成（终态）。 */
    RED_SUCCEEDED(true, false);

    private final boolean terminal;
    private final boolean suspended;

    InvoiceState(boolean terminal, boolean suspended) {
        this.terminal = terminal;
        this.suspended = suspended;
    }

    /** 是否为终态（终态不再接受任何迁移）。 */
    public boolean isTerminal() {
        return terminal;
    }

    /** 是否为挂起态（需要外部事件/人工动作才能继续）。 */
    public boolean isSuspended() {
        return suspended;
    }

    /** 是否已成功开出蓝字发票（含已进入红冲流程的原票）。 */
    public boolean isIssued() {
        return this == SUCCEEDED || this == RED_APPLYING
                || this == RED_APPLY_CONFIRMED || this == RED_SUCCEEDED;
    }

    /** 是否为可补单/可轮询的非终态。 */
    public boolean isInFlight() {
        return this == SUBMITTING || this == WAITING_AUTH || this == PROCESSING;
    }
}
