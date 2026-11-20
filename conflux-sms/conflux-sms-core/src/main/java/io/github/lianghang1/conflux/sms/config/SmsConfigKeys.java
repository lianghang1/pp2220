package io.github.lianghang1.conflux.sms.config;

/**
 * 短信域统一配置键：sms4j {@code BaseConfig} 公共字段中跨渠道同名同义、
 * 发短信必需的「凭证四件套」。这些键在 starter 层结构化声明
 * （{@code SmsProperties.ChannelProperties} 外层字段），装载期下沉合并进
 * 传给 {@code SmsBlendRegistry.register} 的配置 map，键名与 sms4j Config 字段一致。
 *
 * <p>渠道特有字段（如腾讯 sdkAppId、华为 sender、极光 signId）不在此列，
 * 由使用方在 attr 中按 sms4j Config 字段名自定义。</p>
 *
 * @author lianghang
 */
public final class SmsConfigKeys {

    /** 渠道侧访问密钥 ID */
    public static final String ACCESS_KEY_ID = "accessKeyId";
    /** 渠道侧访问密钥 Secret */
    public static final String ACCESS_KEY_SECRET = "accessKeySecret";
    /** 短信签名 */
    public static final String SIGNATURE = "signature";
    /** 模板 ID */
    public static final String TEMPLATE_ID = "templateId";

    private SmsConfigKeys() {
    }
}
