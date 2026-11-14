package io.github.lianghang1.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 渠道回调原始报文（验签前）。
 *
 * <p>与支付回调同理：加密/签名报文解开前拿不到商户号，账户定位只能依赖
 * 回调地址上的 {@code accountKey} 查询参数（见 {@code InvoiceAccountKey}）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceCallbackRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;
    private Map<String, String> queryParams;
    private Map<String, String> formParams;
    private Map<String, String> headers;
    private String body;
    private byte[] rawBytes;
}
