package io.github.lianghang1.conflux.sms.autoconfigure;

import io.github.lianghang1.conflux.sms.config.SmsChannelConfig;
import io.github.lianghang1.conflux.sms.config.SmsConfigProvider;
import io.github.lianghang1.conflux.sms.engine.SmsGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 短信网关自动装配：配置提供者 + 网关门面，与支付/发票 starter 同构。
 *
 * <p>默认提供基于配置文件的 {@link PropertiesSmsConfigProvider}（可被使用方
 * 同类型 Bean 覆盖，如 DB/配置中心实现），注入 {@link SmsGateway}；启动期遍历
 * 已配置渠道预热装载，坏配置 fail-fast。渠道能力全在 sms4j 原始包。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsAutoConfiguration {

    /** 默认配置提供者：读 conflux.sms.apps.*；使用方自定义同类型 Bean 可覆盖。 */
    @Bean
    @ConditionalOnMissingBean(SmsConfigProvider.class)
    public SmsConfigProvider smsConfigProvider(SmsProperties properties) {
        return new PropertiesSmsConfigProvider(properties);
    }

    /** 网关门面：注入配置提供者，启动期预热已配置渠道。 */
    @Bean
    @ConditionalOnMissingBean(SmsGateway.class)
    public SmsGateway smsGateway(SmsConfigProvider configProvider) {
        SmsGateway gateway = SmsGateway.builder().configProvider(configProvider).build();
        // 启动期预热：Properties 实现可遍历全部配置；自定义 provider 无此能力则跳过
        if (configProvider instanceof PropertiesSmsConfigProvider) {
            List<SmsChannelConfig> all = ((PropertiesSmsConfigProvider) configProvider).allConfigs();
            for (SmsChannelConfig cfg : all) {
                gateway.registerChannel(cfg.getAppId(), cfg.getChannel(), cfg.getAttrs());
            }
        }
        log.info("SmsGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
