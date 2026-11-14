package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.invoice.api.InvoiceCallbackRequest;

/**
 * 发票渠道账户标识：回调配置反查的唯一键。
 *
 * <p>沿用支付域修复后的思路：普通接入由 {@code channelAppId} 唯一确定；
 * 同一服务商应用下挂多个销方税号（多企业/多账户）时，必须追加
 * {@code sellerTaxNo} 才能区分，复合键形如 {@code appKey#91310000XXXXXXXXXX}。</p>
 *
 * <p>回调处理器必须先取得配置才能验签/解密，账户定位只能依赖回调地址上的
 * {@link #ROUTING_PARAM} 查询参数；配置装载期必须保证该键全局唯一（fail-fast），
 * 否则回调会归到错误企业或用错密钥。</p>
 *
 * @author lianghang
 */
public final class InvoiceAccountKey {

    /** 回调地址上用于定位账户的查询参数名。 */
    public static final String ROUTING_PARAM = "accountKey";

    /** 渠道应用标识与销方税号的分隔符。 */
    public static final String SEPARATOR = "#";

    private InvoiceAccountKey() {
    }

    /**
     * 配置侧生成账户键：{@code channelAppId} 为空返回 null；
     * {@code sellerTaxNo} 为空时返回 channelAppId（单账户场景与旧键一致），
     * 否则返回 {@code channelAppId#sellerTaxNo}。
     */
    public static String of(String channelAppId, String sellerTaxNo) {
        if (StringUtils.isBlank(channelAppId)) {
            return null;
        }
        if (StringUtils.isBlank(sellerTaxNo)) {
            return channelAppId;
        }
        return channelAppId + SEPARATOR + sellerTaxNo.trim();
    }

    /**
     * 回调侧解析账户键：优先取回调地址/表单上的 {@code accountKey}，
     * 缺省回落渠道原有定位标识（{@code legacyValue}）。
     */
    public static String resolve(InvoiceCallbackRequest raw, String legacyValue) {
        String routing = firstNonBlank(
                raw == null || raw.getQueryParams() == null
                        ? null : raw.getQueryParams().get(ROUTING_PARAM),
                raw == null || raw.getFormParams() == null
                        ? null : raw.getFormParams().get(ROUTING_PARAM));
        return StringUtils.isNotBlank(routing) ? routing : legacyValue;
    }

    /** 是否为多账户场景生成的复合键。 */
    public static boolean isComposite(String accountKey) {
        return accountKey != null && accountKey.contains(SEPARATOR);
    }

    /** 从复合键中取出销方税号部分；非复合键返回 null。 */
    public static String sellerTaxNoOf(String accountKey) {
        if (!isComposite(accountKey)) {
            return null;
        }
        int idx = accountKey.indexOf(SEPARATOR);
        return idx + 1 < accountKey.length() ? accountKey.substring(idx + 1) : null;
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
