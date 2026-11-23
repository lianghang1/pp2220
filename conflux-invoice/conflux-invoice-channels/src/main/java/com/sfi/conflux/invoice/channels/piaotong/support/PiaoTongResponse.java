package com.sfi.conflux.invoice.channels.piaotong.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.sfi.conflux.invoice.channels.piaotong.PiaoTongSpec;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 票通同步响应：信封验签 + 3DES 解密后的<b>业务报文</b>。
 *
 * <p>信封 {@code code=0000} 表示平台受理成功；真实业务结果在解密后的
 * {@code root} 里（{@code code} 字段：0000/6666/7777/9999/3999/4999/5999）。
 * 验签失败按 {@code SIGN_INVALID} 抛出，解密失败按 {@code CALLBACK_PARSE_FAILED}。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PiaoTongResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 信封 code：0000 平台受理成功。 */
    private String code;
    /** 信封 msg。 */
    private String message;
    /** 信封流水号。 */
    private String serialNo;
    /** 原始响应体。 */
    private String rawBody;
    /** 解密后的业务报文 JSON 树（可能为 null：无 content 时）。 */
    private transient JsonNode root;

    /** 平台受理成功（信封层）。 */
    public boolean isAccepted() {
        return PiaoTongSpec.ST_SUCCESS.equals(code);
    }

    /** 取业务报文下的字段（root 缺失返回 null）。 */
    public String bizText(String... names) {
        if (root == null) {
            return null;
        }
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && !node.isNull() && !node.isMissingNode()) {
                String text = node.asText();
                if (text != null && !text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }
}
