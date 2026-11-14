package io.github.lianghang1.conflux.pay.channels.wechat.paymethod;

import io.github.lianghang1.conflux.common.util.JsonUtils;

/**
 * 唤起参数的 JSON 序列化辅助（V2/V3 结果对象统一转 JSON 交付）。
 *
 * @author lianghang
 */
final class JsonPayloads {

    private JsonPayloads() {
    }

    static String toJson(Object result) {
        return JsonUtils.toJson(result);
    }
}
