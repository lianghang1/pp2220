package com.sfi.conflux.notify.config;

import com.sfi.conflux.common.util.StringUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信渠道配置（四域统一的自有配置模型）：公共凭证字段一等化 + 渠道个性 {@link #attr}。
 *
 * <p>与支付域 {@code ChannelConfig}、发票域 {@code ChannelConfig} 同构：
 * 框架理解的公共字段（配置标识、渠道编码、跨渠道同名同义的凭证字段）为一等字段；
 * 厂商特有字段（如腾讯 sdkAppId、华为 sender、极光 signId）放 {@code attr}。
 * <b>本对象不含任何 sms4j 官方类型</b>——装载期由 {@code SmsBlendRegistry} 把公共字段与
 * attr 合并为官方 Config 的键值，官方配置对象的构建完全在框架内部完成。</p>
 *
 * <p>类名沿用 NotificationConfig 以避免与 sms4j 官方 {@code org.dromara.sms4j.provider.config.SmsConfig}
 * 撞名；模型本身与支付/发票渠道配置完全对齐。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class NotificationConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String configId;
    /** 渠道编码（sms4j supplier，如 alibaba/tencent/huawei） */
    private String channelCode;

    // ---- 公共凭证字段：sms4j BaseConfig 中跨渠道同名同义的四件套 ----
    private String accessKeyId;
    private String accessKeySecret;
    private String signature;
    private String templateId;

    /** 厂商特有配置（键名对齐 sms4j 官方 Config 字段） */
    private Map<String, String> attr;

    /** attr 打底，公共字段非空覆盖同名键（公共字段优先）。 */
    public Map<String, String> mergedAttr() {
        Map<String, String> merged = new LinkedHashMap<String, String>();
        if (attr != null) {
            merged.putAll(attr);
        }
        putIfNotBlank(merged, NotificationConfigKeys.ACCESS_KEY_ID, accessKeyId);
        putIfNotBlank(merged, NotificationConfigKeys.ACCESS_KEY_SECRET, accessKeySecret);
        putIfNotBlank(merged, NotificationConfigKeys.SIGNATURE, signature);
        putIfNotBlank(merged, NotificationConfigKeys.TEMPLATE_ID, templateId);
        return merged;
    }

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (!StringUtils.isBlank(value)) {
            map.put(key, value);
        }
    }
}
