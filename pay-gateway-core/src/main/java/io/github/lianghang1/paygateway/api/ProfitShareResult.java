package io.github.lianghang1.paygateway.api;

import io.github.lianghang1.paygateway.model.ProfitShare;
import io.github.lianghang1.paygateway.model.ProfitShareReceiver;
import io.github.lianghang1.paygateway.model.ProfitShareState;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分账结果。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class ProfitShareResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关分账单号 */
    private String shareId;
    /** 使用方应用标识 */
    private String appId;
    /** 使用方业务分账单号 */
    private String bizShareNo;
    /** 关联网关支付单号 */
    private String paymentId;
    /** 分账总金额（分） */
    private Long amount;
    /** 分账接收方列表（含渠道回填的单接收方结果） */
    private List<ProfitShareReceiver> receivers;
    /** 分账单状态 */
    private ProfitShareState state;
    /** 渠道侧分账单号 */
    private String channelShareNo;
    /** 错误码（可空） */
    private String errorCode;
    /** 错误信息（可空） */
    private String errorMsg;
    /** 分账完成时间（可空） */
    private LocalDateTime finishTime;

    /** 由分账单记录构建结果。 */
    public static ProfitShareResult of(ProfitShare share) {
        return new ProfitShareResult()
                .setShareId(share.getShareId())
                .setAppId(share.getAppId())
                .setBizShareNo(share.getBizShareNo())
                .setPaymentId(share.getPaymentId())
                .setAmount(share.getAmount())
                .setReceivers(share.getReceivers())
                .setState(share.getState())
                .setChannelShareNo(share.getChannelShareNo())
                .setErrorCode(share.getErrorCode())
                .setErrorMsg(share.getErrorMsg())
                .setFinishTime(share.getFinishTime());
    }
}
