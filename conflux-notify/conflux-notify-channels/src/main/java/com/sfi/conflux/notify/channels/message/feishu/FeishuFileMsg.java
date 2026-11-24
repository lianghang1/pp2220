package com.sfi.conflux.notify.channels.message.feishu;

import java.util.Collections;
import java.util.Map;

/** 飞书文件消息：content = {"file_key": "..."}（file_key 由上传文件接口获取）。 */
public class FeishuFileMsg extends AbstractFeishuMsg {
    private String fileKey;

    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }

    @Override
    public String msgType() { return "file"; }

    @Override
    public Map<String, Object> content() {
        return Collections.<String, Object>singletonMap("file_key", fileKey);
    }
}
