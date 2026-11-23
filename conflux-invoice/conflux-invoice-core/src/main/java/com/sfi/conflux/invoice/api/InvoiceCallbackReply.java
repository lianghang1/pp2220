package com.sfi.conflux.invoice.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 渠道回调应答报文。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class InvoiceCallbackReply implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String contentType;
    private String body;
    private String message;

    public static InvoiceCallbackReply ok(String body) {
        return new InvoiceCallbackReply().setSuccess(true)
                .setContentType("application/json; charset=utf-8").setBody(body);
    }

    public static InvoiceCallbackReply fail(String message) {
        return new InvoiceCallbackReply().setSuccess(false)
                .setContentType("application/json; charset=utf-8").setMessage(message);
    }
}
