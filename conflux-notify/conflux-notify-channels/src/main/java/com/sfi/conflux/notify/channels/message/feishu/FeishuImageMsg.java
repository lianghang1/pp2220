package com.sfi.conflux.notify.channels.message.feishu;

import java.util.Collections;
import java.util.Map;

/** 飞书图片消息：content = {"image_key": "..."}（image_key 由上传图片接口获取）。 */
public class FeishuImageMsg extends AbstractFeishuMsg {
    private String imageKey;

    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }

    @Override
    public String msgType() { return "image"; }

    @Override
    public Map<String, Object> content() {
        return Collections.<String, Object>singletonMap("image_key", imageKey);
    }
}
