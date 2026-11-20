package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 发票查单请求：渠道定位要素 + 使用方保管的发票载体。
 *
 * <p>SDK 无台账，查单是纯渠道查询——返回渠道归一快照，状态推进由使用方完成。
 * 传入 {@code invoice} 时使用其承载的完整票面与渠道单号；否则用本请求平铺的
 * 定位要素组装最小载体。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String configId;
    /** 渠道编码（必填） */
    private String channelCode;
    /** 使用方业务单号（渠道商户单号） */
    private String bizOrderNo;
    /** 渠道侧单号（已知时优先） */
    private String channelOrderNo;
    /** 发票号码 / 数电发票号码 */
    private String invoiceNo;
    /** 发票代码（税控票） */
    private String invoiceCode;
    /** 使用方保管的发票载体（含完整票面，优先使用） */
    private Invoice invoice;

    /** 参数校验。 */
    public void check() {
        GatewayAssert.param(configId != null && configId.trim().length() > 0, "configId 不能为空");
        GatewayAssert.param(channelCode != null && channelCode.trim().length() > 0, "channelCode 不能为空");
    }
}
