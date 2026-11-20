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
 * 红字确认单（33xx，两段长事务）、版式文件（34xx，固化失败/链接失效等）。
 * 单据状态与金额一律以渠道返回为准，框架不设结果门禁（不因缺 XML、金额不符等否决成功）；
 * 金额校验、判重、状态推进由使用方完成。</p>
 *
 * @author lianghang
 */
public enum InvoiceErrorCode implements ErrorCode {

    // ---- 单据 30xx ----
    INVOICE_NOT_FOUND(3001, "发票单据不存在", 404),

    // ---- 渠道 31xx ----
    CHANNEL_NOT_FOUND(3101, "发票渠道未注册", 500),
    CHANNEL_CAPABILITY_NOT_SUPPORTED(3102, "渠道不支持该能力", 500),
    CHANNEL_INSTANCE_NOT_FOUND(3103, "未找到可用的渠道实例", 500),
    INVOICE_TYPE_NOT_SUPPORTED(3104, "渠道不支持该发票类型", 400),
    CONFIG_MISSING(3105, "发票渠道配置缺失", 500),
    CHANNEL_CALL_FAILED(3107, "发票渠道调用失败", 500),
    CALLBACK_PARSE_FAILED(3108, "发票回调报文解析失败", 400),
    SIGN_INVALID(3109, "发票回调验签失败", 400),

    // ---- 授权会话 32xx ----
    AUTH_SESSION_EXPIRED(3202, "授权会话已过期", 409),
    AUTH_SUBMIT_FAILED(3204, "授权提交失败", 400),

    // ---- 红字确认单 33xx ----
    RED_APPLY_NOT_FOUND(3301, "红字确认单不存在", 404),
    RED_CONFIRM_PENDING(3303, "红字确认单尚未确认，不能开具红字发票", 409),

    // ---- 版式文件 34xx ----
    FILE_NOT_FOUND(3401, "发票版式文件不存在", 404),
    FILE_ARCHIVE_FAILED(3406, "版式文件归档失败", 500),
    FILE_URL_EXPIRED(3407, "版式文件下载地址已失效，请走修复补拉", 502),
    FILE_MATERIALIZE_FAILED(3408, "版式文件固化失败（下载/解码）", 500),

    // ---- 辅助能力 35xx（修复） ----
    REPAIR_PARAM_REQUIRED(3503, "修复请求参数不足", 400);

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
