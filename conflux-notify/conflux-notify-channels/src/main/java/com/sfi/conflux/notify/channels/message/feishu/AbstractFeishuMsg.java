package com.sfi.conflux.notify.channels.message.feishu;

import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.channels.support.NotifyChannels;

import java.util.Map;

/**
 * 飞书消息 bean 公共基类：收敛接收方与 channelCode。
 *
 * <p>飞书官方 SDK 的 content 是 msgType 对应的 JSON 字符串（text/post/interactive/image/file…），
 * 没有 per-类型的强类型入参 bean 可直接继承。故各子类承载该类型的结构化字段，
 * 渠道内用 common 的 JsonUtils 序列化成飞书标准 content JSON，用户侧只填字段。</p>
 *
 * @author lianghang
 */
public abstract class AbstractFeishuMsg implements NotificationMessage {
    /** 接收方 ID（open_id / union_id / user_id / chat_id，由 receiveIdType 指定）。 */
    private String receiveId;
    /** 接收方 ID 类型，默认 open_id。 */
    private String receiveIdType = "open_id";

    public String getReceiveId() { return receiveId; }
    public void setReceiveId(String receiveId) { this.receiveId = receiveId; }
    public String getReceiveIdType() { return receiveIdType; }
    public void setReceiveIdType(String receiveIdType) { this.receiveIdType = receiveIdType; }

    /** 消息类型（飞书 msgType）：text / post / interactive / image / file。 */
    public abstract String msgType();

    /** 消息体，由渠道用 JsonUtils 序列化成飞书 content JSON。 */
    public abstract Map<String, Object> content();

    @Override
    public String channelCode() { return NotifyChannels.FEISHU; }
}
