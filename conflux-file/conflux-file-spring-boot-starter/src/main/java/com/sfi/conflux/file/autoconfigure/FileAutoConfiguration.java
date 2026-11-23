package com.sfi.conflux.file.autoconfigure;

import com.sfi.conflux.file.channel.FileChannelProvider;
import com.sfi.conflux.file.config.FileConfigProvider;
import com.sfi.conflux.file.engine.FileGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 文件网关自动装配：配置提供者 + 网关门面，与支付/发票/短信 starter 同构。
 *
 * <p>默认提供基于配置文件的 {@link PropertiesFileConfigProvider}（可被使用方同类型 Bean
 * 覆盖，如 DB/配置中心实现），注入 {@link FileGateway}；启动期按 configId 聚合全部平台配置
 * 预热装载，坏配置 fail-fast。平台实现与 FileStorageService 构建全在 x-file-storage 原始包。</p>
 *
 * @author lianghang
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(FileProperties.class)
public class FileAutoConfiguration {

    /** 默认配置提供者：读 conflux.file.configs.*；使用方自定义同类型 Bean 可覆盖。 */
    @Bean
    @ConditionalOnMissingBean(FileConfigProvider.class)
    public FileConfigProvider fileConfigProvider(FileProperties properties) {
        return new PropertiesFileConfigProvider(properties);
    }

    /**
     * 网关门面：注入配置提供者；收集使用方声明的 {@link FileChannelProvider} Bean
     * （自定义平台，按 x-file-storage 官方 FileStorage 扩展），内置平台由配置驱动构建。
     * 与三域 starter 收集 {@code List<ChannelProvider>} 的做法同构。
     */
    @Bean
    @ConditionalOnMissingBean(FileGateway.class)
    public FileGateway fileGateway(ObjectProvider<FileConfigProvider> configProvider,
                                   List<FileChannelProvider> providers) {
        FileConfigProvider provider = configProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("未找到 FileConfigProvider Bean");
        }
        FileGateway.Builder builder = FileGateway.builder().configProvider(provider);
        if (providers != null) {
            for (FileChannelProvider channelProvider : providers) {
                builder.channel(channelProvider);
            }
        }
        FileGateway gateway = builder.build();
        log.info("FileGateway 装配完成: channels={}", gateway.registeredChannels());
        return gateway;
    }
}
