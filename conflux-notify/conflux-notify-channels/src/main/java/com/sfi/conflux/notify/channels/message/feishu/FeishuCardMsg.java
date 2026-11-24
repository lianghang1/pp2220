package com.sfi.conflux.notify.channels.message.feishu;

import java.util.Map;

/**
 * 飞书卡片（interactive）消息：content 直接是卡片 JSON 对象。
 *
 * <p>卡片结构复杂且飞书持续演进，框架不重做卡片 DSL——用户按飞书卡片文档构造 Map
 * （或先用 common JsonUtils 把卡片对象转成 Map）传入即可，完整保留 SDK/文档能力。</p>
 */
public class FeishuCardMsg extends AbstractFeishuMsg {
    /** 飞书卡片 JSON 对象（config / elements / header 等）。 */
    private Map<String, Object> card;

    public Map<String, Object> getCard() { return card; }
    public void setCard(Map<String, Object> card) { this.card = card; }

    @Override
    public String msgType() { return "interactive"; }

    @Override
    public Map<String, Object> content() { return card; }
}
