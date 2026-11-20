package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 发票修复请求（从局端补拉已开具发票）。
 *
 * <p>修复是渠道侧补偿动作，<b>不改变网关单据状态机</b>；申请与结果查询分开：
 * {@code repair()} 提交修复申请，{@code queryRepair()} 查询结果（部分渠道为异步）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RepairRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String configId;
    /** 渠道编码（无本地单据时必填，用于定位渠道配置与能力）。 */
    private String channelCode;
    /** 销方税号（无本地单据时必填，与 configId/channelCode 共同定位配置）。 */
    private String sellerTaxNo;
    /** 网关单据号（可选；为空时渠道按票号/日期范围修复）。 */
    private String invoiceId;
    /** 发票代码（税控票）。 */
    private String invoiceCode;
    /** 发票号码 / 数电号码。 */
    private String invoiceNo;
    /** 开票起始日期 yyyy-MM-dd（按日期修复时必填）。 */
    private String startDate;
    /** 开票终止日期 yyyy-MM-dd（按日期修复时必填）。 */
    private String endDate;
    /** 发票类型代码覆盖（fplxdm）。 */
    private String invoiceTypeCode;
    /** 修复订单流水号（为空时由框架生成，结果查询需传同一值）。 */
    private String channelOrderNo;
    /** 渠道自定义参数。 */
    private Map<String, String> extra;
}
