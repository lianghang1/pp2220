package io.github.lianghang1.conflux.pay.autoconfigure;

import io.github.lianghang1.conflux.pay.channel.ChannelAccountKey;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.ChannelParams;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.config.ChannelConfigProvider;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于配置文件的默认 {@link ChannelConfigProvider}：
 * 启动期把 {@code conflux.pay.apps.<appId>.channels.<channel>} 的键值对
 * 经各渠道的 {@code parseParams} 解析为渠道参数对象，并建立
 * 「appId+渠道」与「渠道账户键」两个方向的索引。
 *
 * <p>渠道未实现 {@code parseParams}（返回 null）或配置的渠道未注册时，
 * 对应配置项跳过并告警——该渠道需使用方自行实现配置提供者。</p>
 *
 * <p><b>账户键唯一性</b>：回调反查索引的键由 {@link ChannelAccountKey#of(String, String)}
 * 生成（普通商户 = 渠道侧应用标识；服务商模式 = 应用标识#特约商户号）。
 * 两个配置生成相同账户键时立即抛 {@link GatewayErrorCode#CONFIG_CONFLICT} 使启动失败，
 * 绝不静默覆盖——否则回调会归到错误的 appId 或用错验签密钥，且极难排查。</p>
 *
 * @author lianghang
 */
@Slf4j
public class PropertiesChannelConfigProvider implements ChannelConfigProvider {

    private final PayGatewayProperties properties;
    private final List<ChannelProvider> providers;

    /** appId+渠道 → 配置（下单/退款/查单/对账等正向链路）。 */
    private final Map<String, ChannelConfig> byApp = new HashMap<String, ChannelConfig>();
    /** 渠道编码+账户键 → 配置（回调验签链路，账户键全局唯一）。 */
    private final Map<String, ChannelConfig> byAccountKey = new HashMap<String, ChannelConfig>();

    public PropertiesChannelConfigProvider(PayGatewayProperties properties, List<ChannelProvider> providers) {
        this.properties = properties;
        this.providers = providers;
        buildIndexes();
    }

    private void buildIndexes() {
        Map<String, ChannelProvider> providerByCode = new HashMap<String, ChannelProvider>();
        for (ChannelProvider provider : providers) {
            providerByCode.put(provider.channelCode(), provider);
        }
        for (Map.Entry<String, PayGatewayProperties.AppProperties> appEntry : properties.getApps().entrySet()) {
            String appId = appEntry.getKey();
            for (Map.Entry<String, PayGatewayProperties.ChannelProperties> channelEntry
                    : appEntry.getValue().getChannels().entrySet()) {
                String channelCode = channelEntry.getKey();
                PayGatewayProperties.ChannelProperties channelProps = channelEntry.getValue() == null
                        ? new PayGatewayProperties.ChannelProperties() : channelEntry.getValue();
                ChannelProvider provider = providerByCode.get(channelCode);
                if (provider == null) {
                    log.warn("渠道[{}]未注册，跳过应用[{}]的该渠道配置", channelCode, appId);
                    continue;
                }
                ChannelParams params = provider.parseParams(channelProps.getAttrs());
                if (params == null) {
                    log.warn("渠道[{}]不支持声明式配置，跳过应用[{}]的该渠道配置", channelCode, appId);
                    continue;
                }
                String accountKey = ChannelAccountKey.of(params.channelAppId(), channelProps.getSubMchNo());
                ChannelConfig config = new ChannelConfig()
                        .setAppId(appId)
                        .setChannelCode(channelCode)
                        .setNotifyUrl(channelProps.getNotifyUrl())
                        .setSandbox(channelProps.isSandbox())
                        .setSubMchNo(channelProps.getSubMchNo())
                        .setAccountKey(accountKey)
                        .setParams(params);
                byApp.put(appKey(appId, channelCode), config);
                if (StringUtils.isBlank(accountKey)) {
                    log.warn("渠道[{}]应用[{}]未声明渠道侧应用标识，回调需使用方自行路由", channelCode, appId);
                    continue;
                }
                String key = appKey(channelCode, accountKey);
                ChannelConfig existing = byAccountKey.putIfAbsent(key, config);
                if (existing != null) {
                    throw new GatewayException(GatewayErrorCode.CONFIG_CONFLICT,
                            "渠道账户标识冲突，无法唯一定位回调配置: channel={}, accountKey={}, "
                                    + "appId={} 与 appId={} 生成了相同标识；"
                                    + "服务商模式请为各特约商户配置不同的 subMchNo，"
                                    + "并在回调地址携带 accountKey 查询参数",
                            channelCode, accountKey, existing.getAppId(), appId);
                }
            }
        }
        log.info("渠道配置装载完成: appChannelConfigs={}, uniqueAccountKeys={}",
                byApp.size(), byAccountKey.size());
        if (byAccountKey.size() != byApp.size()) {
            log.warn("有 {} 个配置未生成回调反查键（缺少 channelAppId 或被跳过），"
                            + "这些渠道的回调需使用方自行路由",
                    byApp.size() - byAccountKey.size());
        }
    }

    @Override
    public ChannelConfig getConfig(String appId, String channelCode) {
        ChannelConfig config = byApp.get(appKey(appId, channelCode));
        if (config == null) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "渠道配置缺失: appId={}, channel={}", appId, channelCode);
        }
        return config;
    }

    @Override
    public ChannelConfig getByAccountKey(String channelCode, String accountKey) {
        ChannelConfig config = byAccountKey.get(appKey(channelCode, accountKey));
        if (config == null) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "渠道账户标识无对应配置: channel={}, accountKey={}", channelCode, accountKey);
        }
        return config;
    }

    private String appKey(String left, String right) {
        return left + "::" + right;
    }
}
