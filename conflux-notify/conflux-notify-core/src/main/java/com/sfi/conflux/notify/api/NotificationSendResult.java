package com.sfi.conflux.notify.api;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 归一短信发送结果快照：SDK 无状态，仅返回渠道受理结果的归一视图。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class NotificationSendResult {

    /** 是否受理成功（渠道侧返回成功）。 */
    private boolean success;
    /** 业务流水号（原样回填请求值）。 */
    private String bizId;
    /** 渠道编码。 */
    private String channel;
    /** 使用方应用标识。 */
    private String configId;
    /** 渠道返回原始报文/对象（保留渠道侧细节，便于排查）。 */
    private Object raw;
    /** 失败时的归一错误描述。 */
    private String error;
}
