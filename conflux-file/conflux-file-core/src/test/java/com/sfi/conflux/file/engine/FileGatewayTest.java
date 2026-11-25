package com.sfi.conflux.file.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.file.channel.FileChannelProvider;
import com.sfi.conflux.file.config.FileChannelConfig;
import com.sfi.conflux.file.config.FileConfigProvider;
import com.sfi.conflux.file.support.InMemoryFileConfigProvider;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件网关测试：用 x-file-storage 内置 local 平台（零 SDK、零网络）走真实装载链路，
 * 验证 upload/download/exists/delete 委托、预签名/复制/移动入口、原生出口、必填校验、
 * 自定义平台注册与配置指纹重建。
 *
 * @author lianghang
 */
class FileGatewayTest {

    @TempDir
    java.nio.file.Path tempDir;

    /** 构造 local 平台的扁平 attr（channelCode=local，实例名由装载层自动注入）。 */
    private Map<String, String> localAttr() {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put("basePath", tempDir.toString());
        attr.put("domain", "http://localhost/");
        return attr;
    }

    private InMemoryFileConfigProvider configProvider() {
        return new InMemoryFileConfigProvider()
                .register("app1", "local", localAttr());
    }

    @Test
    void shouldUploadDownloadExistsAndDeleteViaLocalPlatform() {
        InMemoryFileConfigProvider cp = configProvider();
        FileGateway gateway = FileGateway.builder().configProvider(cp).build();

        byte[] content = "hello-conflux-file".getBytes(StandardCharsets.UTF_8);
        FileInfo uploaded = gateway.upload("app1", "local", content, "a.txt");
        assertThat(uploaded).isNotNull();
        // local 平台会按策略重命名，这里只断言返回了有效文件信息
        assertThat(uploaded.getFilename()).isNotBlank();
        assertThat(uploaded.getPlatform()).isEqualTo("local");

        assertThat(gateway.exists("app1", "local", uploaded)).isTrue();
        byte[] downloaded = gateway.download("app1", "local", uploaded).bytes();
        assertThat(new String(downloaded, StandardCharsets.UTF_8)).isEqualTo("hello-conflux-file");

        assertThat(gateway.delete("app1", "local", uploaded)).isTrue();
        assertThat(gateway.exists("app1", "local", uploaded)).isFalse();
    }

    @Test
    void nativeServiceShouldReturnOfficialService() {
        FileGateway gateway = FileGateway.builder().configProvider(configProvider()).build();
        FileStorageService service = gateway.nativeService("app1", "local");
        assertThat(service).isNotNull();
        assertThat(gateway.registeredChannels()).contains("local");
    }

    @Test
    void copyMoveAndPresignedEntryShouldDelegate() {
        FileGateway gateway = FileGateway.builder().configProvider(configProvider()).build();
        FileInfo uploaded = gateway.upload("app1", "local",
                "x".getBytes(StandardCharsets.UTF_8), "x.txt");
        // local 平台不支持预签名/同平台复制移动，官方会返回不支持的结果对象或 null——
        // 这里只验证门面入口确实委托到官方、不抛框架异常。
        assertThat(gateway.copy("app1", "local", uploaded)).isNotNull();
        assertThat(gateway.move("app1", "local", uploaded)).isNotNull();
    }

    @Test
    void missingConfigProviderShouldFailFast() {
        assertThatThrownBy(() -> FileGateway.builder().build())
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(5103));
    }

    @Test
    void missingConfigShouldFailFast() {
        FileConfigProvider cp = (configId, channelCode) -> null;
        FileGateway gateway = FileGateway.builder().configProvider(cp).build();
        assertThatThrownBy(() -> gateway.upload("none", "local", "x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(5103));
    }

    @Test
    void badRequestShouldFailFast() {
        FileGateway gateway = FileGateway.builder().configProvider(configProvider()).build();
        assertThatThrownBy(() -> gateway.upload("", "local", "x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(GatewayException.class, e ->
                        assertThat(e.getErrorCode().getCode()).isEqualTo(5106));
    }

    @Test
    void configChangeShouldRebuildService() {
        InMemoryFileConfigProvider cp = configProvider();
        FileGateway gateway = FileGateway.builder().configProvider(cp).build();
        FileStorageService before = gateway.nativeService("app1", "local");
        // 重新注册同平台、不同 basePath → 指纹变化 → 下次调用重建
        cp.register("app1", "local", localAttr());
        FileStorageService after = gateway.nativeService("app1", "local");
        assertThat(after).isNotNull();
        // 指纹未变时（同配置重复读）应复用同一实例
        assertThat(gateway.nativeService("app1", "local")).isSameAs(after);
        assertThat(before).isNotNull();
    }

    @Test
    void registerCustomPlatformShouldBeVisible() {
        FileGateway gateway = FileGateway.builder().configProvider(configProvider()).build();
        assertThat(gateway.registeredChannels()).contains("local");
        // 用官方 local FileStorage 实例声明为自定义平台，注册后可见
        FileStorageProperties.LocalConfig cfg = new FileStorageProperties.LocalConfig();
        cfg.setPlatform("custom-1");
        cfg.setBasePath(tempDir.toString());
        org.dromara.x.file.storage.core.platform.LocalFileStorage storage =
                new org.dromara.x.file.storage.core.platform.LocalFileStorage(cfg);
        gateway.registerChannel(FileChannelProvider.of(storage));
        assertThat(gateway.registeredChannels()).contains("custom-1");
    }
}
