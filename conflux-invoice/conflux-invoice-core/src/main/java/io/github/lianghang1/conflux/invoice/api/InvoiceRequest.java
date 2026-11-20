package io.github.lianghang1.conflux.invoice.api;

import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 蓝字发票开具请求（框架统一入口）。
 *
 * <p>{@code channelCode} 可空：留空时由 {@code ChannelInstanceRouter} 按
 * configId + 销方税号 + 票种做通道分级择优；显式指定则直连该渠道（用于灰度/降级）。</p>
 *
 * <p>金额单位为「元」；{@code amount} 为含税合计，必须与明细行合计一致（引擎校验）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填）。 */
    private String configId;
    /** 使用方业务单号（必填，渠道商户单号；查重由使用方负责）。 */
    private String bizOrderNo;
    /** 指定渠道编码（可空，空则按通道分级路由）。 */
    private String channelCode;
    /** 销方纳税人识别号（必填，企业准入标识）。 */
    private String sellerTaxNo;
    private String sellerName;
    /** 购方纳税人识别号（普票可空）。 */
    private String buyerTaxNo;
    /** 购方名称（必填）。 */
    private String buyerTitle;
    private String buyerAddress;
    private String buyerBank;
    private String buyerPhone;
    /** 购方手机号（电票交付：短信地址，与邮箱都传时渠道只发邮箱）。 */
    private String buyerMobile;
    /** 购方邮箱（电票交付；多个用英文逗号分隔）。 */
    private String buyerEmail;
    /** 票种（必填，框架统一语义，由渠道映射为自身编码）。 */
    private InvoiceType invoiceType;
    /** 明细行（至少 1 行）。 */
    private List<InvoiceItem> items;
    /** 含税合计（元，必填）。 */
    private BigDecimal amount;
    /** 税额合计（元）。 */
    private BigDecimal taxAmount;
    private String remark;
    private String drawer;
    /**
     * 特殊票种标志：00 普通票（默认）、01 成品油、03 建筑服务、04 货物运输服务、
     * 05 不动产销售、06 不动产经营租赁、09 旅客运输服务等（渠道 tspz）。
     * 为空且存在 {@link #jzfwTdys}/{@link #bdcTdysList} 时由渠道适配器自动推断。
     */
    private String tspz;
    /** 征收方式：0 普通征税（默认）、1 减按计征、2 差额征收-差额开票、3 差额征收-全额开票、4 减按征收-经营租赁。 */
    private String zsfs;
    /** 建筑服务特定业务要素（tspz=03 必填；键值见渠道文档，如 tdzzsxmbh / jzfwfsd / jzxmmc / kdsbz / kqysssxbgglbm）。 */
    private Map<String, String> jzfwTdys;
    /** 不动产经营租赁特定业务要素列表（tspz=06 必填，行数与商品行数一致；键值见渠道文档，如 bdcdz / zlqqz / cqzsh / kdsbz）。 */
    private List<Map<String, String>> bdcTdysList;
    /** 是否允许渠道转异步（默认 true，数电票常见长耗时）。 */
    private boolean asyncAllowed = true;
    /** 使用方异步通知地址。 */
    private String notifyUrl;
    /** 沙箱开关。 */
    private boolean sandbox;
    /** 扩展字段（渠道个性参数）。 */
    private Map<String, String> attr;
}
