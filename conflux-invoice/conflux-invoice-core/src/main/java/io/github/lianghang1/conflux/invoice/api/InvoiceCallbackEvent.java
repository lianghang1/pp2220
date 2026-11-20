package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.invoice.model.InvoiceFile;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.RedApplyState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 渠道回调解析结果（已验签、已归一化为框架语义）。
 *
 * <p>回调既可能推进蓝字开票（开票完成/失败/转异步），也可能推进红字确认单
 * （对方确认/拒绝），因此同时携带 {@code state} 与 {@code redState}。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceCallbackEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关侧定位字段（渠道能在报文中带回时优先使用）。 */
    private String invoiceId;
    private String redApplyId;
    private String channelOrderNo;
    private String bizOrderNo;
    /** 蓝票单据状态迁移目标（可空表示不涉及蓝票）。 */
    private InvoiceState state;
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    /** 回调内联的版式文件（XML 法定原件可能在回调里直接给出）。 */
    private List<InvoiceFile> files;
    /** 红字确认单状态迁移目标（可空表示不涉及红冲）。 */
    private RedApplyState redState;
    private String redConfirmNo;
    private String errorCode;
    private String errorMsg;
    /** 验签/解析后的渠道原始报文。 */
    private String rawPayload;
    /** 渠道附加字段。 */
    private Map<String, String> attr;
}
