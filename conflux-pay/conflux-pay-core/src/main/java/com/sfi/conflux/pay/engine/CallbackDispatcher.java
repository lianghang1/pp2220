package com.sfi.conflux.pay.engine;

import com.sfi.conflux.pay.api.CallbackEvent;
import com.sfi.conflux.pay.api.ChannelCallbackRequest;
import com.sfi.conflux.pay.channel.ChannelCallbackHandler;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.CommonErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 渠道回调解析器：定位渠道回调处理器 → 验签解析 → 归一化为统一事件。
 *
 * <p>SDK 无状态：只做验签与归一，不查订单、不校验金额、不推进状态。
 * 归一事件携带渠道回传的业务单号，使用方据此定位自己的订单，判重、
 * 金额校验与状态推进由使用方完成。验签失败、报文不合法等异常直接上抛，
 * 使用方捕获后可用渠道处理器的 {@code failureReply()} 应答渠道触发重推。</p>
 *
 * @author lianghang
 */
class CallbackDispatcher {

    private final GatewayRuntime rt;

    CallbackDispatcher(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 验签并解析渠道回调，返回归一化事件。configId 由使用方查库后显式传入。 */
    CallbackEvent parse(String configId, ChannelCallbackRequest raw) {
        if (raw == null || StringUtils.isBlank(raw.getChannelCode())) {
            throw GatewayException.of(CommonErrorCode.BAD_REQUEST, "回调请求缺少渠道编码");
        }
        if (StringUtils.isBlank(configId)) {
            throw GatewayException.of(CommonErrorCode.BAD_REQUEST, "回调缺少 configId，无法定位渠道配置");
        }
        normalizeHeaders(raw);
        ChannelCallbackHandler handler = rt.registry.callbackHandler(raw.getChannelCode());
        ChannelConfig config = rt.configProvider.getConfig(configId, raw.getChannelCode());
        CallbackEvent event = handler.parseCallback(raw, config);
        if (event == null || event.getType() == null) {
            throw GatewayException.of(GatewayErrorCode.CALLBACK_PARSE_FAILED, "回调解析结果为空");
        }
        return event;
    }

    /**
     * 请求头归一化为大小写不敏感视图：HTTP 头名本就不区分大小写，
     * 不同 Web 容器传给使用方的头名大小写各异，渠道验签按规范名查找时必须免疫差异。
     */
    private void normalizeHeaders(ChannelCallbackRequest raw) {
        Map<String, String> headers = raw.getHeaders();
        if (headers == null) {
            raw.setHeaders(new HashMap<String, String>());
            return;
        }
        if (!(headers instanceof TreeMap)) {
            Map<String, String> caseInsensitive = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            caseInsensitive.putAll(headers);
            raw.setHeaders(caseInsensitive);
        }
    }
}
