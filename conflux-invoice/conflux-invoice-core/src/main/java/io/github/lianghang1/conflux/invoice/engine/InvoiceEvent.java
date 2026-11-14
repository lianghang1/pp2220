package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发票域事件：状态发生实质迁移后发布，供宿主做下游通知、审计、对账。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型。 */
    public enum Type {
        CREATED,
        SUBMITTED,
        WAITING_AUTH,
        PROCESSING,
        SUCCEEDED,
        FAILED,
        RED_APPLIED,
        RED_CONFIRMED,
        RED_ISSUED,
        AUTH_VERIFIED,
        CALLBACK_RECEIVED
    }

    private Type type;
    private Invoice invoice;
    private RedApply redApply;
    private LocalDateTime occurredTime;
    private String message;
}
