package io.github.lianghang1.conflux.sms.channels.aliyun;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.sms.channel.SmsParams;
import io.github.lianghang1.conflux.sms.exception.SmsErrorCode;
import lombok.Data;

import java.util.Map;

/**
 * 阿里云短信渠道参数（薄门面，底层 SDK 为 sms4j 的 AlibabaConfig）。
 *
 * <p>声明式配置键（attrs）：{@code accessKeyId}、{@code accessKeySecret}、
 * {@code signature}、{@code templateId}（必填），{@code templateName}、{@code regionId}（选填）。
 * 键名由本渠道定义，配置来源不限（文件/DB/配置中心均可，由 SmsConfigProvider 提供）。</p>
 *
 * @author lianghang
 */
@Data
public class AliyunParams implements SmsParams {

    private String accessKeyId;
    private String accessKeySecret;
    private String signature;
    private String templateId;
    private String templateName;
    private String regionId;

    /** 从 attrs 键值对构建（与支付域 XxxParams.fromMap 同构）。 */
    public static AliyunParams fromMap(Map<String, String> properties) {
        AliyunParams p = new AliyunParams();
        if (properties != null) {
            p.setAccessKeyId(properties.get("accessKeyId"));
            p.setAccessKeySecret(properties.get("accessKeySecret"));
            p.setSignature(properties.get("signature"));
            p.setTemplateId(properties.get("templateId"));
            p.setTemplateName(properties.get("templateName"));
            p.setRegionId(properties.get("regionId"));
        }
        if (StringUtils.isBlank(p.getAccessKeyId()) || StringUtils.isBlank(p.getAccessKeySecret())
                || StringUtils.isBlank(p.getSignature()) || StringUtils.isBlank(p.getTemplateId())) {
            throw new GatewayException(SmsErrorCode.CONFIG_MISSING,
                    "阿里云短信参数不完整，必填键: accessKeyId/accessKeySecret/signature/templateId");
        }
        return p;
    }

    @Override
    public String channelAppId() {
        return accessKeyId;
    }
}
