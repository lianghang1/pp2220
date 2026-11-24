package com.sfi.conflux.notify.channels.message.feishu;

import java.util.Collections;
import java.util.Map;

/** 飞书文本消息：content = {"text": "..."}。 */
public class FeishuTextMsg extends AbstractFeishuMsg {
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    @Override
    public String msgType() { return "text"; }

    @Override
    public Map<String, Object> content() {
        return Collections.<String, Object>singletonMap("text", text);
    }
}
