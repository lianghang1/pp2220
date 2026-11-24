package com.sfi.conflux.notify.channels.message.feishu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书富文本（post）消息。
 *
 * <p>content 结构：{@code {"zh_cn":{"title":"...","content":[[{"tag":"text","text":"..."}]]}}}。
 * 提供 {@link #addTextLine} 便捷方法拼纯文本行；复杂元素（a/at/image）可直接用
 * {@link #setContent} 传飞书原生段落结构，框架不重做飞书富文本元素体系。</p>
 */
public class FeishuPostMsg extends AbstractFeishuMsg {
    private String locale = "zh_cn";
    private String title;
    /** 段落：List<行>，每行 List<元素>，元素为 tag/text 等 Map。 */
    private List<List<Map<String, Object>>> content = new ArrayList<List<Map<String, Object>>>();

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<List<Map<String, Object>>> getContent() { return content; }
    public void setContent(List<List<Map<String, Object>>> content) { this.content = content; }

    /** 追加一个纯文本行。 */
    public FeishuPostMsg addTextLine(String text) {
        Map<String, Object> el = new LinkedHashMap<String, Object>();
        el.put("tag", "text");
        el.put("text", text);
        List<Map<String, Object>> line = new ArrayList<Map<String, Object>>();
        line.add(el);
        content.add(line);
        return this;
    }

    @Override
    public String msgType() { return "post"; }

    @Override
    public Map<String, Object> content() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("title", title);
        body.put("content", content);
        return Collections.<String, Object>singletonMap(locale, body);
    }
}
