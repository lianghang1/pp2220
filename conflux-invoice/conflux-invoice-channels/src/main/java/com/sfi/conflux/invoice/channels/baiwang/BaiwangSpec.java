package com.sfi.conflux.invoice.channels.baiwang;

import com.sfi.conflux.invoice.model.InvoiceType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * 百望云渠道常量：编码、默认地址、method、状态码、票种映射。
 *
 * <p>取值来自《开票结果查询接口》《销项回调接口文档》及各接口 SDK demo：
 * 开票状态 status 00-开票中/01-完成/02-失败/03-已作废/04-作废中；
 * 回调 type 01-开票成功/02-开票失败/05-删除；
 * 红字确认单状态 redConfirmStatus、操作 confirmType 均按对应接口文档。</p>
 *
 * @author lianghang
 */
public final class BaiwangSpec {

    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String CODE = "baiwang";
    public static final String DISPLAY_NAME = "百望云";

    public static final String DEFAULT_BASE_URL = "https://openapi.baiwang.com/router/rest";
    public static final String SANDBOX_BASE_URL = "https://sandbox-openapi.baiwang.com/router/rest";

    // ---- 业务接口 method ----
    public static final String METHOD_INVOICE = "baiwang.s.outputinvoice.invoice";
    public static final String METHOD_QUERY = "baiwang.s.outputinvoice.query";
    public static final String METHOD_FAST_RED = "baiwang.s.outputinvoice.fastRed";
    public static final String METHOD_PUSH = "baiwang.s.outputinvoice.push";
    public static final String METHOD_RETRY = "baiwang.s.outputinvoice.retry";
    public static final String METHOD_RED_CONFIRM_OPERATE = "baiwang.s.redconfirm.operate";
    public static final String METHOD_RED_CONFIRM_QUERY = "baiwang.s.redconfirm.query";

    // ---- 回调 method ----
    public static final String METHOD_CALLBACK_INVOICE = "baiwang.s.callback.invoice";
    public static final String METHOD_CALLBACK_RED_CONFIRM = "baiwang.s.callback.redconfirm";
    public static final String METHOD_CALLBACK_RED_INFO = "baiwang.s.callback.redinfo";
    public static final String METHOD_CALLBACK_SCAN = "baiwang.s.callback.scan";

    // ---- 开票状态 status（开票结果查询）----
    public static final String ST_PROCESSING = "00";
    public static final String ST_SUCCESS = "01";
    public static final String ST_FAILED = "02";
    public static final String ST_VOIDED = "03";
    public static final String ST_VOIDING = "04";

    // ---- 回调 type（baiwang.s.callback.invoice）----
    public static final String CALLBACK_SUCCESS = "01";
    public static final String CALLBACK_FAILED = "02";
    public static final String CALLBACK_DELETED = "05";

    /** 红字确认单状态：01 无需确认/确认成功，02 否认/失败（回调与查询口径）。 */
    public static final String RED_CONFIRMED = "01";
    public static final String RED_REJECTED = "02";

    // ---- 开票请求固定值 ----
    /** 开票类型：1 蓝票（红票由冲红接口处理）。 */
    public static final String INVOICE_TYPE_BLUE = "1";
    /** 单价含税标志：0 不含税。 */
    public static final String PRICE_TAX_EXCLUSIVE = "0";
    /** 明细标志：1 清单。 */
    public static final String DETAIL_MARK_YES = "1";

    // ---- 红冲 ----
    /** 快捷冲红类型：0 仅生成红字信息表/确认单，1 生成后自动开具红票。 */
    public static final String FAST_RED_APPLY_ONLY = "0";
    public static final String FAST_RED_AUTO_ISSUE = "1";
    /** 确认单操作：01 确认、02 否认、03 撤销。 */
    public static final String CONFIRM_TYPE_ACCEPT = "01";
    public static final String CONFIRM_TYPE_REJECT = "02";
    public static final String CONFIRM_TYPE_CANCEL = "03";
    /** 确认单查询操作类型：2 库里查询。 */
    public static final String RED_CONFIRM_OP_QUERY = "2";
    /** 操作方身份：0 销方。 */
    public static final String SELECTOR_SELLER = "0";
    /** 红字确认等待小时数。 */
    public static final long RED_CONFIRM_HOURS = 72L;

    private static final Map<InvoiceType, String> TYPE_CODES = new EnumMap<InvoiceType, String>(InvoiceType.class);

    static {
        // 文档发票种类编码：01 数电专票、02 数电普票、004 增值税专票、007 增值税普票、87 机动车
        TYPE_CODES.put(InvoiceType.DIGITAL_VAT_SPECIAL, "01");
        TYPE_CODES.put(InvoiceType.DIGITAL_VAT_GENERAL, "02");
        TYPE_CODES.put(InvoiceType.VAT_SPECIAL, "004");
        TYPE_CODES.put(InvoiceType.VAT_GENERAL, "007");
        TYPE_CODES.put(InvoiceType.VEHICLE_SALES, "87");
    }

    private BaiwangSpec() {
    }

    /** 开票时间戳（文档 orderDateTime：yyyy-MM-dd HH:mm:ss）。 */
    public static String nowText() {
        return LocalDateTime.now().format(ORDER_TIME);
    }

    /** 框架票种 → 文档 invoiceTypeCode（默认，可由 attr 覆盖）。 */
    public static String invoiceTypeCode(InvoiceType type) {
        return type == null ? null : TYPE_CODES.get(type);
    }
}
