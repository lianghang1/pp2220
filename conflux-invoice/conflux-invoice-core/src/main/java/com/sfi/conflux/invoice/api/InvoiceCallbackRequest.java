package com.sfi.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 渠道回调原始报文（验签前）。
 *
 * <p>回调验签由框架负责；配置定位由使用方按 configId（+ sellerTaxNo）查库后显式传入。</p>
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
