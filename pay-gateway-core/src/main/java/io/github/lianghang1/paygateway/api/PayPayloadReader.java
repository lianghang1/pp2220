package io.github.lianghang1.paygateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lianghang1.paygateway.util.JsonUtils;

/**
 * PayPayload JSON 读写辅助：支付单内以 JSON 字符串存储唤起参数，
 * 本类负责安全还原，供结果组装与使用方重复获取。
 *
 * @author lianghang
 */
public final class PayPayloadReader {

    private static final TypeReference<PayPayload> PAYLOAD_TYPE = new TypeReference<PayPayload>() { };

    private PayPayloadReader() {
    }

    /** JSON 字符串还原为 {@link PayPayload}；空白或非法内容返回 null。 */
    public static PayPayload read(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JsonUtils.fromJson(json, PAYLOAD_TYPE);
        } catch (Exception e) {
            return null;
        }
    }
}
