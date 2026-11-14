package io.github.lianghang1.conflux.invoice.channels.mock;

import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelParams;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * mock 渠道参数：用配置控制渠道行为，便于在单测/example 里覆盖各条链路，
 * 无需任何真实凭据或网络。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class MockInvoiceParams implements InvoiceChannelParams, Serializable {

    private static final long serialVersionUID = 1L;

    private String channelAppId = "mock-app";
    private String sellerTaxNo;
    private String appSecret = "mock-secret";

    /**
     * 蓝字开票行为：
     * AUTH（先要求短信授权，授权后成功，默认）、ASYNC（异步受理）、
     * SUCCESS（同步成功）、FAIL（失败）。
     */
    private String issueOutcome = "AUTH";
    /** 红字确认单查询结果：CONFIRMED / WAITING_CONFIRM / REJECTED。 */
    private String redConfirmOutcome = "CONFIRMED";

    @Override
    public String channelAppId() {
        return channelAppId;
    }

    @Override
    public String sellerTaxNo() {
        return sellerTaxNo;
    }
}
