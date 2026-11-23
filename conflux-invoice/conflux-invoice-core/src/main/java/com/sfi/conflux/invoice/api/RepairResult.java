package com.sfi.conflux.invoice.api;

import com.sfi.conflux.invoice.model.InvoiceFile;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 发票修复 / 修复结果查询结果。
 *
 * <p>{@link #state} 表达修复任务本身的状态，与网关 {@code InvoiceState} 无关，
 * 避免为补偿动作污染单据状态机。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class RepairResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 修复任务状态。 */
    public enum RepairState {
        /** 已受理，等待结果查询或回调。 */
        PROCESSING,
        /** 已修复到发票。 */
        SUCCEEDED,
        /** 修复失败或未修复到发票。 */
        FAILED
    }

    private RepairState state = RepairState.PROCESSING;
    /** 修复订单流水号（结果查询用）。 */
    private String channelOrderNo;
    /** 修复到的版式文件（可为 URL 或内联内容）。 */
    private List<InvoiceFile> files;
    /** 票面字段摘要（渠道返回的原始字段）。 */
    private String invoiceNo;
    private String invoiceCode;
    private String errorCode;
    private String errorMsg;
    /** 渠道原始报文。 */
    private String rawPayload;

    public static RepairResult accepted(String channelOrderNo) {
        return new RepairResult().setState(RepairState.PROCESSING)
                .setChannelOrderNo(channelOrderNo);
    }

    public static RepairResult succeeded(String channelOrderNo, List<InvoiceFile> files) {
        return new RepairResult().setState(RepairState.SUCCEEDED)
                .setChannelOrderNo(channelOrderNo)
                .setFiles(files == null ? Collections.<InvoiceFile>emptyList() : files);
    }

    public static RepairResult failed(String errorCode, String errorMsg) {
        return new RepairResult().setState(RepairState.FAILED)
                .setErrorCode(errorCode).setErrorMsg(errorMsg);
    }
}
