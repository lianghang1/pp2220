package io.github.lianghang1.conflux.file.autoconfigure;

import io.github.lianghang1.conflux.file.config.FileConfigProvider;
import io.github.lianghang1.conflux.file.engine.FileGateway;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 conflux.file.configs.<configId>.<channelCode> 配置被 starter 装载进官方 FileStorageService，
 * 即「配置规则是我们的、平台能力来自 x-file-storage」。用 local 平台，不触发网络。
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
                Collections.<io.github.lianghang1.conflux.file.channel.FileChannelProvider>emptyList());
    }

    private FileStorageProperties localProps(String channelCode) {
        FileStorageProperties props = new FileStorageProperties();
        FileStorageProperties.LocalConfig local = new FileStorageProperties.LocalConfig();
        local.setPlatform(channelCode);
        local.setBasePath(System.getProperty("java.io.tmpdir"));
        local.setDomain("http://localhost/");
        props.setLocal(Collections.singletonList(local));
        return props;
    }

    @Test
    void shouldLoadOurAppsStructureIntoGateway() {
        FileProperties props = new FileProperties();
        props.getConfigs().put("demo-boot",
                Collections.singletonMap("local-1", localProps("local-1")));

        FileGateway gw = gateway(props);

        assertThat(gw.registeredChannels()).contains("local-1");
    }

    @Test
    void propertiesProviderShouldReadConfigByAppAndPlatform() {
        FileProperties props = new FileProperties();
        props.getConfigs().put("demo-boot",
                Collections.singletonMap("local-1", localProps("local-1")));
        FileConfigProvider provider = new PropertiesFileConfigProvider(props);
        assertThat(provider.getConfig("demo-boot", "local-1").getChannelCode()).isEqualTo("local-1");
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
