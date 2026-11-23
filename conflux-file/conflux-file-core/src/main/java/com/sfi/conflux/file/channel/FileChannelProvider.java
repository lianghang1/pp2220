package com.sfi.conflux.file.channel;

import org.dromara.x.file.storage.core.platform.FileStorage;

/**
 * 文件渠道声明对象：与支付域 {@code ChannelProvider}、发票域 {@code InvoiceChannelProvider}、
 * 通知域 {@code SmsChannelProvider} 处于同一层——承载一个 x-file-storage 原生
 * {@link FileStorage} 实例。
 *
 * <p>平台的实现（{@code FileStorage} / 可选 {@code FileStorageClientFactory}）全部按
 * <a href="https://x-file-storage.xuyanwu.cn/">x-file-storage 官方文档</a>由官方提供或由
 * 使用方自行扩展，本类只是把一个已构建好的 FileStorage 实例包装成四域统一的 Provider
 * 形态：注册时经官方入口 {@code FileStorageServiceBuilder.addFileStorage(fileStorage)} 注入，
 * 不含任何平台业务代码。内置平台无需本类（由配置 + 官方 builder 构建）；本类仅用于
 * 声明配置清单外的自定义平台。</p>
 *
 * @author lianghang
 */
public final class FileChannelProvider {

    private final FileStorage fileStorage;

    private FileChannelProvider(FileStorage fileStorage) {
        if (fileStorage == null) {
            throw new IllegalArgumentException("FileStorage 不能为空");
        }
        this.fileStorage = fileStorage;
    }

    /**
     * 声明一个自定义存储平台：直接接收使用方按 x-file-storage 官方文档实现的
     * {@link FileStorage} 实例（可已由官方 builder 静态方法构建并注入 clientFactory）。
     */
    public static FileChannelProvider of(FileStorage fileStorage) {
        return new FileChannelProvider(fileStorage);
    }

    /** 平台实例标识（取 FileStorage 的 platform）。 */
    public String channelCode() {
        return fileStorage.getPlatform();
    }

    /** x-file-storage 原生 FileStorage 实例，供注册进 FileStorageServiceBuilder。 */
    public FileStorage fileStorage() {
        return fileStorage;
    }

    @Override
    public String toString() {
        return "FileChannelProvider{channelCode='" + channelCode() + "'}";
    }
}
