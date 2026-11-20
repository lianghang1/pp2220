package io.github.lianghang1.conflux.invoice.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 发票载体：承载完整票面与渠道要素，随开票/查单结果返回、随后续请求回传。
 *
 * <p>SDK 无台账——本对象是渠道交互与使用方业务之间的数据载体，不做持久化。
 * <b>开票状态以渠道返回为准</b>：框架不因缺少版式文件（含 XML 法定原件）而否决成功；
 * 版式文件能取到就固化进返回载体，由使用方按业务与合规要求自行归档。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class Invoice implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关发票单据号（主键）。 */
    private String invoiceId;
    /** 使用方应用标识。 */
    private String configId;
    /** 使用方业务单号（渠道商户单号；查重由使用方负责）。 */
    private String bizOrderNo;
    /** 实际使用的渠道编码。 */
    private String channelCode;
    /** 实际使用的渠道实例标识。 */
    private String channelInstanceId;

    // ---- 票面 ----
    /** 销方纳税人识别号（企业准入标识，也是多销方路由维度）。 */
    private String sellerTaxNo;
    private String sellerName;
    /** 购方纳税人识别号（普票可空）。 */
    private String buyerTaxNo;
    private String buyerTitle;
    private String buyerAddress;
    private String buyerBank;
    private String buyerPhone;
    /** 购方手机号（电票交付：短信地址，与邮箱都传时渠道只发邮箱）。 */
    private String buyerMobile;
    /** 购方邮箱（电票交付；多个用英文逗号分隔）。 */
    private String buyerEmail;
    /** 框架统一票种。 */
    private InvoiceType invoiceType;
    /** 明细行。 */
    private List<InvoiceItem> items;
    /** 含税合计（元）。 */
    private BigDecimal amount;
    /** 税额合计（元）。 */
    private BigDecimal taxAmount;
    private String remark;
    private String drawer;

    /**
     * 特殊票种标志（渠道 tspz）：00 普通票（默认）、01 成品油、03 建筑服务、
     * 04 货物运输服务、05 不动产销售、06 不动产经营租赁、09 旅客运输服务等。
     *
     * <p>为空且存在 {@link #jzfwTdys} 时按 03（建筑服务）上报；
     * 为空且存在 {@link #bdcTdysList} 时按 06（不动产经营租赁）上报。</p>
     */
    private String tspz;
    /** 征收方式：0 普通征税（默认）、1 减按计征、2 差额征收-差额开票、3 差额征收-全额开票、4 减按征收-经营租赁。 */
    private String zsfs;
    /**
     * 建筑服务特定业务要素（tspz=03 时必填）：
     * {@code tdzzsxmbh 跨区域涉税项目编号、jzfwfsd 建筑服务发生地、jzxmmc 建设项目名称、
     * kdsbz 跨地(Y/N)标志、kqysssxbgglbm 跨区域涉税事项报告编码、fullAddress 详细地址}。
     */
    private Map<String, String> jzfwTdys;
    /**
     * 不动产经营租赁特定业务要素列表（tspz=06 时必填，行数须与商品行数一致）：
     * {@code bdcdz 不动产地址、zlqqz 租赁期起止、kdsbz 跨地(Y/N)标志、cqzsh 产权证书号、
     * dw 单位、fullAddress 详细地址}。
     */
    private List<Map<String, String>> bdcTdysList;

    /** 是否允许渠道转异步。 */
    private boolean asyncAllowed;
    /** 使用方异步通知地址。 */
    private String notifyUrl;
    private boolean sandbox;

    // ---- 状态 ----
    private InvoiceState state;
    /** 当前授权会话（WAITING_AUTH 时非空）。 */
    private String authSessionId;
    /** 渠道侧流水号/申请号。 */
    private String channelOrderNo;
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private LocalDateTime invoiceTime;
    private String errorCode;
    private String errorMsg;
    /** 当前关联的红字确认单（进入红冲流程后非空）。 */
    private String redApplyId;
    /** 红字发票指向的原蓝票。 */
    private String originalInvoiceId;

    /** 扩展字段（渠道回传的自定义信息）。 */
    private Map<String, String> attr;
}
