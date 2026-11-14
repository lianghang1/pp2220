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
 * 发票单据实体（持久化对象）。
 *
 * <p><b>并发契约</b>：{@link #version} 是乐观锁版本号。引擎每次状态迁移都通过
 * {@code InvoiceStore.tryUpdate(invoice, expectedVersion)} 做 CAS 写入，写入失败
 * 即放弃本次迁移并让补单/回调重试——避免支付域里「全量覆盖且无 expectedState」
 * 导致的回调与补单互相覆盖。{@link #claimOwner} / {@link #claimExpireTime}
 * 是补单扫描的租约字段，配合 {@code claimProcessingBefore} 原子认领，避免多实例重复处理。</p>
 *
 * <p><b>法定原件</b>：开票成功的判定不仅看渠道状态码，还要求归档至少一份
 * {@code InvoiceFileType.XML} 原件，文件单独存 {@code InvoiceFileStore}。</p>
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
    private String appId;
    /** 使用方业务单号（与 appId 组成幂等键）。 */
    private String bizOrderNo;
    /** 实际使用的渠道编码。 */
    private String channelCode;
    /** 实际使用的渠道实例标识。 */
    private String channelInstanceId;
    /** 渠道账户键（回调反查用）。 */
    private String accountKey;

    // ---- 票面 ----
    /** 销方纳税人识别号（企业准入标识，也是多账户路由键）。 */
    private String sellerTaxNo;
    private String sellerName;
    /** 购方纳税人识别号（普票可空）。 */
    private String buyerTaxNo;
    private String buyerTitle;
    private String buyerAddress;
    private String buyerBank;
    private String buyerPhone;
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

    // ---- 并发控制 ----
    /** 乐观锁版本号（CAS 契约，由 Store 递增）。 */
    private long version;
    /** 补单租约持有者。 */
    private String claimOwner;
    /** 补单租约到期时间。 */
    private LocalDateTime claimExpireTime;

    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    /** 扩展字段（渠道回传的自定义信息）。 */
    private Map<String, String> attrs;
}
