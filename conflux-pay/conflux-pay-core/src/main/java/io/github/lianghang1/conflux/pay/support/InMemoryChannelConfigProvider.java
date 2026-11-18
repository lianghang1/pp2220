package io.github.lianghang1.conflux.pay.support;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.channel.ChannelAccountKey;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.config.ChannelConfigProvider;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道配置提供者内存实现：装载期建立双索引（appId+渠道、渠道账户键）。
 *
 * <p><b>账户键唯一性 fail-fast</b>：与发票域 {@code InMemoryInvoiceConfigProvider}
 * 及 starter 配置文件实现同理，两个配置生成相同账户键时立即抛
 * {@code CONFIG_CONFLICT} 使装配失败，绝不静默覆盖——否则回调会归到错误的
 * appId 或用错验签密钥。</p>
 *
 * <p>注册时若未显式设置 accountKey，将依据
 * {@code ChannelAccountKey.of(params.channelAppId(), subMchNo)} 自动派生；
 * 无法派生（params 或 channelAppId 缺失）则跳过账户键反查索引。</p>
 *
 * <p>主要供纯 jar 场景与单测使用；Spring Boot 场景由 starter 提供
 * 基于配置文件的实现。</p>
 *
 * @author lianghang
 */
public class InMemoryChannelConfigProvider implements ChannelConfigProvider {

    /** appId + 渠道编码 → 配置（下单/退款/查单/对账等正向链路）。 */
    private final Map<String, ChannelConfig> byApp = new ConcurrentHashMap<String, ChannelConfig>();
    /** 渠道编码 + 账户键 → 配置（回调验签链路，账户键全局唯一）。 */
    private final Map<String, ChannelConfig> byAccountKey = new ConcurrentHashMap<String, ChannelConfig>();

    /** 注册一份渠道配置；账户键冲突时立即失败。 */
    public InMemoryChannelConfigProvider register(ChannelConfig config) {
        String appKey = config.getAppId() + "#" + config.getChannelCode();
        byApp.put(appKey, config);

        String accountKey = config.getAccountKey();
        if (accountKey == null && config.getParams() != null) {
            accountKey = ChannelAccountKey.of(config.getParams().channelAppId(), config.getSubMchNo());
        }
        if (accountKey != null) {
            String accountIndexKey = config.getChannelCode() + "#" + accountKey;
            ChannelConfig existing = byAccountKey.putIfAbsent(accountIndexKey, config);
            if (existing != null) {
                throw new GatewayException(GatewayErrorCode.CONFIG_CONFLICT,
                        "渠道账户键冲突: channel={}, accountKey={}, appId={} 与 appId={} 生成了相同标识",
                        config.getChannelCode(), accountKey, existing.getAppId(), config.getAppId());
            }
        }
        return this;
    }

    @Override
    public ChannelConfig getConfig(String appId, String channelCode) {
        ChannelConfig config = byApp.get(appId + "#" + channelCode);
        if (config == null) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "渠道配置缺失: appId={}, channel={}", appId, channelCode);
        }
        return config;
    }

    @Override
    public ChannelConfig getByAccountKey(String channelCode, String accountKey) {
        ChannelConfig config = byAccountKey.get(channelCode + "#" + accountKey);
        if (config == null) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "渠道账户键无对应配置: channel={}, accountKey={}", channelCode, accountKey);
        }
        return config;
    }
}
