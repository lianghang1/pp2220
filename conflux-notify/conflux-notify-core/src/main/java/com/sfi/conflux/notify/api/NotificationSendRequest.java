package com.sfi.conflux.notify.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一短信发送请求：实现 {@link NotificationMessage}，作为通知域的一种消息形态。
 *
 * <p>{@code channelCode} 由消息自带（sms4j 供应商码，如 alibaba/tencent）；
 * {@code bizId} 为使用方生成的业务流水号，SDK 仅原样回填、不生成、不判重；
 * 判重、频率控制、落库由使用方在自己的业务链完成（与 pay/invoice 域「SDK 无状态」一致）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class NotificationSendRequest implements NotificationMessage {

    /** 渠道编码：sms4j 供应商标识（alibaba/tencent/huawei…）。 */
    private String channelCode;

    /** 业务流水号（使用方生成，SDK 不判重）。 */
    private String bizId;
    /** 目标手机号。 */
    private String phone;
    /** 短信内容（模板变量模式时为变量值载体，见 params）。 */
    private String content;
    /** 模板变量（键为模板占位符，值为填充值）；无需模板变量的渠道可为空。 */
    private Map<String, String> params = new LinkedHashMap<String, String>();

    @Override
    public String channelCode() {
        return channelCode;
    }
}
