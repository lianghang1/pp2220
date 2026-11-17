package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.invoice.model.RedApply;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 快捷红冲结果：一次调用覆盖红冲全流程，用 outcome 区分走到了哪一步。
 *
 * <p>{@link Outcome#ACCEPTED} 表示渠道无需确认、红票已提交（红票结果在
 * {@code redInvoice}，最终状态由回调/查单推进）；{@link Outcome#WAITING_CONFIRM}
 * 表示仍需对方确认，红票尚未开具，由使用方后续确认并调用 issueRed。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class QuickRedResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Outcome {
        /** 无需确认，红票已提交（在途）。 */
        ACCEPTED,
        /** 需对方确认，红票未开具。 */
        WAITING_CONFIRM
    }

    private Outcome outcome;
    /** 红字确认单（两种情况均返回）。 */
    private RedApply redApply;
    /** 红字发票受理结果（仅 ACCEPTED 时有值）。 */
    private InvoiceResult redInvoice;

    public boolean isAccepted() {
        return outcome == Outcome.ACCEPTED;
    }

    public boolean isWaitingConfirm() {
        return outcome == Outcome.WAITING_CONFIRM;
    }

    public static QuickRedResult accepted(RedApply redApply, InvoiceResult redInvoice) {
        return new QuickRedResult().setOutcome(Outcome.ACCEPTED)
                .setRedApply(redApply).setRedInvoice(redInvoice);
    }

    public static QuickRedResult waitingConfirm(RedApply redApply) {
        return new QuickRedResult().setOutcome(Outcome.WAITING_CONFIRM)
                .setRedApply(redApply);
    }
}
