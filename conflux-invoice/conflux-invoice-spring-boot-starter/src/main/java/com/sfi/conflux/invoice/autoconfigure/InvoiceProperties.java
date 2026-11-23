package com.sfi.conflux.invoice.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发票 starter 配置（{@code conflux.invoice.*}）。
 *
 * <p>结构为「应用 → 渠道 → 配置项」。相较支付域多了
 * {@code sellerTaxNo}（企业准入标识）与 {@code tier/priority}（通道分级路由），
 * 因为同一应用下可能有多个销方税号、同一税号又可能有多级通道。</p>
 *
 * <p>渠道声明路径为 {@code conflux.invoice.configs.<configId>.<channelCode>.*}：
 * 第一层 key 为应用标识，第二层 key 为渠道编码。</p>
 *
 * @author lianghang
 */
@Data
@ConfigurationProperties(prefix = "conflux.invoice")
public class InvoiceProperties {

    /** 应用 → 渠道配置：第一层 key 为应用标识，第二层 key 为渠道编码。 */
    private Map<String, Map<String, ChannelProperties>> configs =
            new LinkedHashMap<String, Map<String, ChannelProperties>>();

    /** 是否注册 mock 渠道（默认 true，便于零凭据启动演示）。 */
    private boolean mockEnabled = true;

    /** 版式文件固化选项。 */
    private FileProperties file = new FileProperties();

    /** 版式文件固化配置（url/Base64 统一拉平为字节，可选本地落盘）。 */
    @Data
    public static class FileProperties {
        /** 归档时对 url 型文件立即下载固化（XML 法定原件始终强制，不随开关变化）。 */
        private boolean immediateDownload = true;
        /** 本地落盘根目录；留空 = 不落盘，字节仅内联在文件载体。 */
        private String outputDir;
        /** 固化下载超时（毫秒）。 */
        private long downloadTimeoutMillis = 30000L;
    }

    /** 单渠道配置：统一基础键 + attr 渠道个性键值包。 */
    @Data
    public static class ChannelProperties {
        /** 销方纳税人识别号（企业准入标识，也是多账户路由键）。 */
        private String sellerTaxNo;
        /** 渠道异步通知地址。 */
        private String notifyUrl;
        private boolean sandbox;
        /** 通道分级：DIRECT_TAX / SERVICE_API / WEB_PORTAL，留空取渠道默认值。 */
        private String tier;
        /** 同级别内权重，数值越大越优先。 */
        private int priority;
        private boolean enabled = true;
        /** 渠道个性键值包（经 InvoiceChannelProvider.parseParams 解析）。 */
        private Map<String, String> attr = new LinkedHashMap<String, String>();
    }
}
