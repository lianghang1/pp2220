package com.sfi.conflux.invoice.api;

import com.sfi.conflux.common.exception.GatewayAssert;
import com.sfi.conflux.invoice.model.Invoice;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 红字确认单申请请求（两段红冲的第一段）。
 *
 * <p>SDK 无台账——原票要素由使用方随 {@code originalInvoice} 回传（开票时 SDK
 * 已把完整载体返回给使用方保管）；渠道编码也可由请求显式指定，为空时取原票载体上的渠道。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RedApplyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用方应用标识（必填） */
    private String configId;
    /** 使用方业务单号（渠道幂等键，必填） */
    private String bizRedNo;
    /** 红冲原因（必填） */
    private String reason;
    /**
     * 冲红原因代码：1 开票有误（全额红冲）、2 销货退回、3 服务终止、4 销售折让。
     * 为空时渠道适配器按 1 上报。
     */
    private String chyydm;
    /** 红冲金额（元，为空表示全额红冲原票） */
    private BigDecimal amount;
    /** 指定渠道（为空取原票载体的渠道） */
    private String channelCode;
    /** 渠道回调地址（可选） */
    private String notifyUrl;
    /** 原蓝字发票载体（必填，含票号/代码/金额/明细等完整要素） */
    private Invoice originalInvoice;

    /** 参数校验。 */
    public void check() {
        GatewayAssert.param(configId != null && configId.trim().length() > 0, "configId 不能为空");
        GatewayAssert.param(bizRedNo != null && bizRedNo.trim().length() > 0, "bizRedNo 不能为空");
        GatewayAssert.param(reason != null && reason.trim().length() > 0, "红冲原因不能为空");
        GatewayAssert.param(originalInvoice != null, "originalInvoice 原票载体不能为空");
    }
}
