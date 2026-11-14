package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.api.ChannelCallbackRequest;
import io.github.lianghang1.paygateway.util.StringUtils;

/**
 * 渠道账户标识：回调配置反查的唯一键。
 *
 * <p>普通商户模式下账户由 {@link ChannelParams#channelAppId()} 唯一确定；服务商模式下
 * 同一渠道应用标识下可挂多个特约商户，需追加 {@code ChannelConfig.subMchNo} 才能区分。
 * 本类把「配置侧生成」与「回调侧解析」收敛到同一套规则，避免两边算法漂移导致静默串户。</p>
 *
 * <p>回调处理器必须先取得配置才能验签/解密（加密报文尚未解开时拿不到其中的商户号），
 * 因此账户定位只能依赖回调地址的查询参数：服务商模式必须在回调 URL 上显式携带
 * {@link #ROUTING_PARAM}，不能指望从通知报文里反推。</p>
 *
 * @author lianghang
 */
public final class ChannelAccountKey {

    /** 回调地址上用于定位账户的查询参数名。 */
    public static final String ROUTING_PARAM = "accountKey";

    /** 服务商模式下渠道应用标识与特约商户号的分隔符。 */
    public static final String SEPARATOR = "#";

    private ChannelAccountKey() {
    }

    /**
     * 配置侧生成账户键：{@code channelAppId} 为空返回 null；
     * {@code subMchNo} 为空时返回 channelAppId（与普通商户的旧反查键完全一致，
     * 保证存量配置零改动可用），否则返回 {@code channelAppId#subMchNo}。
     */
    public static String of(String channelAppId, String subMchNo) {
        if (StringUtils.isBlank(channelAppId)) {
            return null;
        }
        if (StringUtils.isBlank(subMchNo)) {
            return channelAppId;
        }
        return channelAppId + SEPARATOR + subMchNo.trim();
    }

    /**
     * 回调侧解析账户键：优先取回调地址/表单上的 {@code accountKey}，
     * 缺省回落渠道原有定位标识（{@code legacyValue}），保证旧回调 URL 无需改造。
     */
    public static String resolve(ChannelCallbackRequest raw, String legacyValue) {
        String routing = firstNonBlank(
                raw == null || raw.getQueryParams() == null
                        ? null : raw.getQueryParams().get(ROUTING_PARAM),
                raw == null || raw.getFormParams() == null
                        ? null : raw.getFormParams().get(ROUTING_PARAM));
        return StringUtils.isNotBlank(routing) ? routing : legacyValue;
    }

    /** 是否为服务商模式生成的复合键（含分隔符）。 */
    public static boolean isComposite(String accountKey) {
        return accountKey != null && accountKey.contains(SEPARATOR);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
