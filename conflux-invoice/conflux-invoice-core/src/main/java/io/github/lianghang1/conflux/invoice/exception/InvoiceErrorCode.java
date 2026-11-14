package io.github.lianghang1.conflux.invoice.exception;

import io.github.lianghang1.conflux.common.exception.ErrorCode;

/**
 * 发票域错误码（3xxx 段）。
 *
 * <p>错误码按能力域分段：1xxx 通用（common.CommonErrorCode）、2xxx 支付（pay.GatewayErrorCode）、
 * 3xxx 发票（本枚举）。渠道专属错误在渠道包内定义，统一收敛为
 * {@link #CHANNEL_CALL_FAILED} 并携带渠道原始错误信息。</p>
 *
 * <p>发票相较支付多出三类语义：授权会话（32xx，扫脸/短信/扫码会过期）、
 * 红字确认单（33xx，两段长事务）、版式文件法定原件（34xx，XML 必须归档）。</p>
 *
 * @author lianghang
 */
public enum InvoiceErrorCode implements ErrorCode {

    // ---- 单据 30xx ----
    INVOICE_NOT_FOUND(3001, "发票单据不存在", 404),
    DUPLICATE_BIZ_ORDER(3002, "相同业务单号的开票请求已存在且参数不一致", 409),
    STATE_ILLEGAL(3003, "发票当前状态不允许该操作", 409),
    AMOUNT_MISMATCH(3004, "渠道回传金额与请求金额不一致", 409),
    ITEM_REQUIRED(3005, "发票明细行不能为空", 400),
    TAX_NO_REQUIRED(3006, "缺少销方纳税人识别号", 400),
    BUYER_REQUIRED(3007, "缺少购方名称", 400),

    // ---- 渠道 31xx ----
    CHANNEL_NOT_FOUND(3101, "发票渠道未注册", 500),
    CHANNEL_CAPABILITY_NOT_SUPPORTED(3102, "渠道不支持该能力", 500),
    CHANNEL_INSTANCE_NOT_FOUND(3103, "未找到可用的渠道实例", 500),
    INVOICE_TYPE_NOT_SUPPORTED(3104, "渠道不支持该发票类型", 400),
    CONFIG_MISSING(3105, "发票渠道配置缺失", 500),
    CONFIG_CONFLICT(3106, "发票渠道账户键冲突，无法唯一定位回调配置", 500),
    CHANNEL_CALL_FAILED(3107, "发票渠道调用失败", 500),
    CALLBACK_PARSE_FAILED(3108, "发票回调报文解析失败", 400),
    SIGN_INVALID(3109, "发票回调验签失败", 400),

    // ---- 授权会话 32xx ----
    AUTH_SESSION_NOT_FOUND(3201, "授权会话不存在", 404),
    AUTH_SESSION_EXPIRED(3202, "授权会话已过期", 409),
    AUTH_REQUIRED(3203, "该渠道需要先完成授权（扫脸/短信/扫码）", 409),
    AUTH_SUBMIT_FAILED(3204, "授权提交失败", 400),
    AUTH_TYPE_NOT_SUPPORTED(3205, "渠道不支持该授权方式", 400),
    AUTH_SESSION_STATE_ILLEGAL(3206, "授权会话当前状态不允许提交", 409),

    // ---- 红字确认单 33xx ----
    RED_APPLY_NOT_FOUND(3301, "红字确认单不存在", 404),
    RED_APPLY_STATE_ILLEGAL(3302, "红字确认单当前状态不允许该操作", 409),
    RED_CONFIRM_PENDING(3303, "红字确认单尚未确认，不能开具红字发票", 409),
    RED_CONFIRM_EXPIRED(3304, "红字确认单已过期", 409),
    RED_CONFIRM_REJECTED(3305, "红字确认单被对方拒绝", 409),

    // ---- 文件 / 持久化 34xx ----
    FILE_NOT_FOUND(3401, "发票版式文件不存在", 404),
    XML_ORIGINAL_REQUIRED(3402, "缺少法定 XML 原件，禁止置为开票成功", 500),
    STORE_MISSING(3403, "持久化实现未注册", 500),
    STORE_CONCURRENT_MODIFICATION(3404, "单据已被并发修改，请重试", 409),
    STORE_CLAIM_FAILED(3405, "待处理单据认领失败", 409),
    FILE_ARCHIVE_FAILED(3406, "版式文件归档失败", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    InvoiceErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
