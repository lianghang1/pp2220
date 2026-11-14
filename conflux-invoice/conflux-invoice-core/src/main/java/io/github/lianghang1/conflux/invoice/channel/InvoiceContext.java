package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.AuthSession;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道调用上下文：把「配置 + 实例 + 单据」一次性交给渠道能力实现。
 *
 * <p>渠道实现无状态、线程安全，所有运行态数据都从本上下文取得；
 * 框架与渠道之间只通过本对象与 api 包的返回值交互，渠道不直接改实体。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private ChannelConfig config;
    private ChannelInstance instance;
    private Invoice invoice;
    private InvoiceRequest request;
    private RedApply redApply;
    private AuthSession authSession;

    public static InvoiceContext of(ChannelConfig config, ChannelInstance instance, Invoice invoice) {
        return new InvoiceContext().setConfig(config).setInstance(instance).setInvoice(invoice);
    }

    /** 取配置，缺失时按 CONFIG_MISSING 快速失败。 */
    public ChannelConfig requireConfig() {
        if (config == null) {
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING, "渠道调用上下文缺少 ChannelConfig");
        }
        return config;
    }

    /** 取发票单据，缺失时按 INVOICE_NOT_FOUND 快速失败。 */
    public Invoice requireInvoice() {
        if (invoice == null) {
            throw new GatewayException(InvoiceErrorCode.INVOICE_NOT_FOUND, "渠道调用上下文缺少 Invoice");
        }
        return invoice;
    }
}
