package io.github.lianghang1.conflux.sms.channels.aliyun;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.sms.api.SmsSendRequest;
import io.github.lianghang1.conflux.sms.api.SmsSendResult;
import io.github.lianghang1.conflux.sms.channel.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.channel.SmsSender;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import org.dromara.sms4j.aliyun.config.AlibabaConfig;
import org.dromara.sms4j.aliyun.service.AlibabaSmsImpl;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;

import java.util.LinkedHashMap;

/**
 * 阿里云短信发送能力（薄门面）：把渠道参数桥接为 sms4j 的 {@link AlibabaConfig}，
 * 直接实例化 {@link AlibabaSmsImpl} 发送并归一为 {@link SmsSendResult}。
 *
 * <p>刻意不使用 {@code SmsFactory} 的全局实例缓存——每次按配置构造无状态实例，
 * 与 Conflux「SDK 无状态、客户端按凭证构建」的既有模式一致，也避免跨 appId 串配置。</p>
 *
 * @author lianghang
 */
public class AliyunSender implements SmsSender {

    @Override
    public SmsSendResult send(SmsSendRequest request, SmsChannelConfig config) {
        AliyunParams params = (AliyunParams) config.getParams();
        if (params == null) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING, "阿里云短信渠道参数为空");
        }
        try {
            AlibabaConfig alibabaConfig = toAlibabaConfig(params);
            SmsBlend smsBlend = new AlibabaSmsImpl(alibabaConfig);
            SmsResponse response = dispatch(smsBlend, request);
            boolean ok = response != null && response.isSuccess();
            return new SmsSendResult()
                    .setSuccess(ok)
                    .setRaw(response == null ? null : response.getData())
                    .setError(ok ? null : (response == null ? "渠道返回空结果" : String.valueOf(response.getData())));
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(SmsErrorCode.CHANNEL_CALL_FAILED,
                    "阿里云短信发送失败: " + e.getMessage(), e);
        }
    }

    /** 请求参数 → sms4j 发送重载：有模板变量走 Map 重载，否则走文本重载。 */
    private SmsResponse dispatch(SmsBlend smsBlend, SmsSendRequest request) {
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            return smsBlend.sendMessage(request.getPhone(),
                    new LinkedHashMap<String, String>(request.getParams()));
        }
        return smsBlend.sendMessage(request.getPhone(), request.getContent());
    }

    /** 把我们的 AliyunParams 桥接为 sms4j 的 AlibabaConfig（薄门面核心转换）。 */
    AlibabaConfig toAlibabaConfig(AliyunParams params) {
        AlibabaConfig cfg = new AlibabaConfig();
        cfg.setAccessKeyId(params.getAccessKeyId());
        cfg.setAccessKeySecret(params.getAccessKeySecret());
        cfg.setSignature(params.getSignature());
        cfg.setTemplateId(params.getTemplateId());
        if (params.getTemplateName() != null) {
            cfg.setTemplateName(params.getTemplateName());
        }
        if (params.getRegionId() != null) {
            cfg.setRegionId(params.getRegionId());
        }
        return cfg;
    }
}
