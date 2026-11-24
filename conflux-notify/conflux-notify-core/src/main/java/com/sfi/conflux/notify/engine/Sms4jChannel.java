package com.sfi.conflux.notify.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendRequest;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channel.AbstractNotificationChannel;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.notify.exception.NotificationErrorCode;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;

import java.util.LinkedHashMap;

/**
 * 短信渠道：把 sms4j 原始装载链路（{@link SmsBlendRegistry}）包装成统一 {@code NotificationChannel}。
 *
 * <p><b>sms4j 装载层零改动</b>：本类只做三件事——从自有配置装载 SmsBlend、按请求参数选发送重载、
 * 把 {@link SmsResponse} 归一为 {@link NotificationSendResult}。指纹缓存、线程池初始化、
 * configId::channel 隔离、Factory 注册全部仍在 SmsBlendRegistry 内部，语义与改造前完全一致。</p>
 *
 * <p>本类注册进网关的统一渠道表，键为 sms4j 供应商码（alibaba/tencent/…）。微信/飞书等 IM
 * 渠道与本类平起平坐，门面路由无任何差别。短信请求参数校验下沉到本渠道（门面只做路由）。</p>
 *
 * @author lianghang
 */
public class Sms4jChannel extends AbstractNotificationChannel {

    private final String supplier;
    private final SmsBlendRegistry registry;

    public Sms4jChannel(String supplier, SmsBlendRegistry registry) {
        this.supplier = supplier;
        this.registry = registry;
    }

    @Override
    public String channelCode() {
        return supplier;
    }

    @Override
    protected NotificationSendResult doSend(NotificationConfig config, NotificationMessage message) {
        NotificationSendRequest request = requireSmsRequest(message);
        String configId = config == null ? null : config.getConfigId();
        registry.ensureLoaded(config);
        SmsBlend blend = registry.smsBlend(configId, supplier);
        SmsResponse response = dispatch(blend, request);
        boolean ok = response != null && response.isSuccess();
        NotificationSendResult result = new NotificationSendResult().setSuccess(ok);
        if (response != null) {
            result.setRaw(response.getData());
            if (!ok) {
                result.setError(String.valueOf(response.getData()));
            }
        } else {
            result.setError("渠道返回空结果");
        }
        return result;
    }

    /** 短信渠道只接受 NotificationSendRequest；phone 必填校验在此完成（门面只路由）。 */
    private NotificationSendRequest requireSmsRequest(NotificationMessage message) {
        if (!(message instanceof NotificationSendRequest)) {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST,
                    "短信渠道仅接受 NotificationSendRequest, 实际={}",
                    message == null ? null : message.getClass().getName());
        }
        NotificationSendRequest request = (NotificationSendRequest) message;
        if (StringUtils.isBlank(request.getPhone())) {
            throw new GatewayException(NotificationErrorCode.BAD_REQUEST, "发送参数不完整: phone 必填");
        }
        return request;
    }

    /** 请求参数 → sms4j 发送重载：有模板变量走 Map 重载，否则走文本重载。 */
    private SmsResponse dispatch(SmsBlend blend, NotificationSendRequest request) {
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            return blend.sendMessage(request.getPhone(),
                    new LinkedHashMap<String, String>(request.getParams()));
        }
        return blend.sendMessage(request.getPhone(), request.getContent());
    }
}
