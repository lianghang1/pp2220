package io.github.lianghang1.conflux.pay.channels.chinaums;

import io.github.lianghang1.conflux.pay.channel.ChannelParams;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import lombok.Data;

import java.util.Map;

/**
 * 银联商务天满渠道参数（OPEN-BODY-SIG 认证模式）。
 *
 * <p>声明式配置键（均放在渠道 {@code attrs} 下）：
 * <ul>
 *   <li>{@code appId}（必填）产品 ID，由银联商务分配；</li>
 *   <li>{@code appKey}（必填）产品密钥；</li>
 *   <li>{@code mid}（必填）商户号；</li>
 *   <li>{@code tid}（必填）终端号；</li>
 *   <li>{@code subAppId}（选填）微信开放平台 appId（微信渠道下单需要）；</li>
 *   <li>{@code gateway}（选填）自定义网关前缀，缺省按 sandbox 切换正式/测试地址。</li>
 * </ul>
 *
 * <p>{@code notifyUrl} 为统一键，由 {@link io.github.lianghang1.conflux.pay.channel.ChannelConfig} 承载。</p>
 *
 * @author lianghang
 */
@Data
public class ChinaumsParams implements ChannelParams {

    /** 生产网关前缀 */
    public static final String DEFAULT_GATEWAY = "https://api-mop.chinaums.com";
    /** 测试网关前缀 */
    public static final String SANDBOX_GATEWAY = "https://test-api-open.chinaums.com";

    /** 产品 ID */
    private String appId;
    /** 产品密钥 */
    private String appKey;
    /** 商户号 */
    private String mid;
    /** 终端号 */
    private String tid;
    /** 微信开放平台 appId（微信渠道下单用） */
    private String subAppId;
    /** 自定义网关前缀（显式配置优先于 sandbox 开关） */
    private String gateway;

    /** 生效网关前缀：显式 gateway 优先，否则按 sandbox 切换。 */
    public String effectiveGateway(boolean sandbox) {
        if (StringUtils.isNotBlank(gateway)) {
            return gateway.endsWith("/") ? gateway.substring(0, gateway.length() - 1) : gateway;
        }
        return sandbox ? SANDBOX_GATEWAY : DEFAULT_GATEWAY;
    }

    /** 从 attrs 构建；必填项缺失抛配置异常。 */
    public static ChinaumsParams fromMap(Map<String, String> properties) {
        ChinaumsParams params = new ChinaumsParams();
        if (properties != null) {
            params.setAppId(properties.get("appId"));
            params.setAppKey(properties.get("appKey"));
            params.setMid(properties.get("mid"));
            params.setTid(properties.get("tid"));
            params.setSubAppId(properties.get("subAppId"));
            params.setGateway(properties.get("gateway"));
        }
        if (StringUtils.isBlank(params.getAppId()) || StringUtils.isBlank(params.getAppKey())
                || StringUtils.isBlank(params.getMid()) || StringUtils.isBlank(params.getTid())) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "天满渠道参数不完整，必填键: appId/appKey/mid/tid");
        }
        return params;
    }

    @Override
    public String channelAppId() {
        // 回调配置反查使用商户号（通知报文携带 mid）
        return mid;
    }

    /** 关键凭证字段参与相等性判断（客户端缓存复用/签名身份依据）。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChinaumsParams)) {
            return false;
        }
        ChinaumsParams that = (ChinaumsParams) other;
        return eq(appId, that.appId) && eq(appKey, that.appKey) && eq(mid, that.mid)
                && eq(tid, that.tid) && eq(subAppId, that.subAppId) && eq(gateway, that.gateway);
    }

    @Override
    public int hashCode() {
        int h = appId == null ? 0 : appId.hashCode();
        h = h * 31 + (mid == null ? 0 : mid.hashCode());
        return h;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
