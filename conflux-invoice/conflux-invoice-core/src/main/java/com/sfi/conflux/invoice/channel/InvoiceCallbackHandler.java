package com.sfi.conflux.invoice.channel;

import com.sfi.conflux.invoice.api.InvoiceCallbackEvent;
import com.sfi.conflux.invoice.api.InvoiceCallbackReply;
import com.sfi.conflux.invoice.api.InvoiceCallbackRequest;

/**
 * 回调解析能力：验签 + 归一化 + 生成应答报文。
 *
 * <p>验签失败必须抛 {@code SIGN_INVALID}；解析失败抛 {@code CALLBACK_PARSE_FAILED}。
 * 配置由使用方按 configId + channelCode + sellerTaxNo 查库后经门面传入，
 * 框架不再从回调报文隐式反查账户键。</p>
 *
 * @author lianghang
 */
public interface InvoiceCallbackHandler {

    InvoiceCallbackEvent parse(InvoiceCallbackRequest raw, ChannelConfig config);

    InvoiceCallbackReply reply(boolean success, String message);
}
