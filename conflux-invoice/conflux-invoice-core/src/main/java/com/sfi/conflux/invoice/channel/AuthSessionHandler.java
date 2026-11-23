package com.sfi.conflux.invoice.channel;

import com.sfi.conflux.invoice.api.AuthResult;
import com.sfi.conflux.invoice.api.AuthSubmission;

/**
 * 授权会话能力：处理会过期的税局登录态、扫脸、短信、扫码。
 *
 * <p>支付域没有对应能力；这是数电票接入必须单独暴露的端口。</p>
 *
 * @author lianghang
 */
public interface AuthSessionHandler {

    /** 发起授权，返回需展示给使用方的挑战（二维码/扫脸地址/短信提示）。 */
    AuthResult start(InvoiceContext ctx);

    /** 提交用户凭证（短信码/扫码 token/人脸结果）。 */
    AuthResult submit(InvoiceContext ctx, AuthSubmission submission);
}
