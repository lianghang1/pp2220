package com.sfi.conflux.invoice.api;

import com.sfi.conflux.invoice.model.RedApplyState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道快捷红冲结果：渠道一次调用后，红票已开具或仍需对方确认。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class QuickRedOutcome implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 红票开具受理结果（已开具时有值）。 */
    private InvoiceResult redInvoice;
    /** 确认单结果：确认状态、编号、截止时间（需确认时有值）。 */
    private RedApplyResult redApply;

    public boolean isIssued() {
        return redInvoice != null;
    }

    public boolean isWaitingConfirm() {
        return !isIssued() && redApply != null
                && redApply.getState() == RedApplyState.WAITING_CONFIRM;
    }

    public static QuickRedOutcome issued(InvoiceResult redInvoice) {
        return new QuickRedOutcome().setRedInvoice(redInvoice);
    }

    public static QuickRedOutcome issued(InvoiceResult redInvoice, RedApplyResult redApply) {
        return new QuickRedOutcome().setRedInvoice(redInvoice).setRedApply(redApply);
    }

    public static QuickRedOutcome waiting(RedApplyResult redApply) {
        return new QuickRedOutcome().setRedApply(redApply);
    }

    public static QuickRedOutcome failed(String errorCode, String errorMsg) {
        return new QuickRedOutcome().setRedApply(RedApplyResult.failed(errorCode, errorMsg));
    }
}
