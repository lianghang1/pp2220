package io.github.lianghang1.conflux.invoice.channels.baiwangjs.support;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 百旺金穗云同步响应（{@code code/message/renson/...}）。
 *
 * <p>平台所有开具/红冲/登录接口都是<b>异步</b>：同步响应只表示「是否受理」，
 * 真实业务结果在 {@code text/plain} Base64 回调里。因此本对象只承载受理状态与
 * 少量即时字段（如 3.1.1 的 {@code data.qyId}、3.4.4 的 {@code data.ewm}）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class BaiwangJsResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** "0" 成功受理；"2001" 短信验证码已发送；"1004" 需要登录；其它失败。 */
    private String code;
    private String message;
    /** 文档字段名原文为 renson（拼写如此）。 */
    private String reason;
    private String rawBody;
    /** 原始 JSON 树，供读取 data 下的即时字段。 */
    private transient JsonNode root;

    public boolean isAccepted() {
        return "0".equals(code);
    }

    public boolean isSmsSent() {
        return "2001".equals(code);
    }

    public boolean needsLogin() {
        return "1004".equals(code);
    }

    public boolean needsAuth() {
        return "1003".equals(code);
    }

    /** 取 data 下字段，data 缺失时回落根节点（兼容乐企通道 Data 大写）。 */
    public String dataText(String... names) {
        JsonNode data = root == null ? null : firstNonNull(root.get("data"), root.get("Data"));
        String fromData = BaiwangJsCodec.text(data, names);
        return fromData != null ? fromData : BaiwangJsCodec.text(root, names);
    }

    private static JsonNode firstNonNull(JsonNode a, JsonNode b) {
        return a != null && !a.isNull() ? a : b;
    }
}
