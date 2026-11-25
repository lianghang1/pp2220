package com.sfi.conflux.file.autoconfigure;

import com.sfi.conflux.file.config.FileConfigProvider;
import com.sfi.conflux.file.engine.FileGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 conflux.file.configs.<configId>.<channelCode> 扁平配置被 starter 装载进官方
 * FileStorageService，即「配置规则是我们的、平台能力来自 x-file-storage」。用 local 平台，不触发网络。
 *
 * @author lianghang
 */
class FileAutoConfigurationTest {

    @SuppressWarnings("unchecked")
    private FileGateway gateway(FileProperties props) {
        FileAutoConfiguration auto = new FileAutoConfiguration();
        FileConfigProvider provider = auto.fileConfigProvider(props);
        ObjectProvider<FileConfigProvider> configProvider = mock(ObjectProvider.class);
        when(configProvider.getIfAvailable()).thenReturn(provider);
        return auto.fileGateway(configProvider,
                Collections.<com.sfi.conflux.file.channel.FileChannelProvider>emptyList());
    }

    /** 扁平 local 配置：basePath 指向临时目录，attr 键名对齐官方 LocalConfig 字段。 */
    private FileProperties.ChannelProperties localProps() {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("basePath", System.getProperty("java.io.tmpdir"));
        attr.put("domain", "http://localhost/");
        FileProperties.ChannelProperties props = new FileProperties.ChannelProperties();
        props.setAttr(attr);
        return props;
    }

    @Test
    void shouldLoadOurAppsStructureIntoGateway() {
        FileProperties props = new FileProperties();
        props.getConfigs().put("demo-boot",
                Collections.singletonMap("local", localProps()));

        FileGateway gw = gateway(props);

        assertThat(gw.registeredChannels()).contains("local");
    }

    @Test
    void propertiesProviderShouldReadConfigByAppAndChannelCode() {
        FileProperties props = new FileProperties();
        props.getConfigs().put("demo-boot",
                Collections.singletonMap("local", localProps()));
        FileConfigProvider provider = new PropertiesFileConfigProvider(props);
        assertThat(provider.getConfig("demo-boot", "local").getChannelCode()).isEqualTo("local");
        assertThat(provider.getConfig("demo-boot", "local").getAttr()).containsKey("basePath");
        assertThat(provider.allConfigs()).hasSize(1);
        assertThatThrownBy(() -> provider.getConfig("demo-boot", "missing"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void emptyPropertiesShouldProduceEmptyGateway() {
        FileGateway gw = gateway(new FileProperties());
        assertThat(gw.registeredChannels()).isEmpty();
    }
}
