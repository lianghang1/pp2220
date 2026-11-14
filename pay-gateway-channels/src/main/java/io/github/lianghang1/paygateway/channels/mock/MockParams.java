package io.github.lianghang1.paygateway.channels.mock;

import io.github.lianghang1.paygateway.channel.ChannelParams;

import java.util.Map;

/**
 * 模拟渠道参数。
 *
 * <p>声明式配置键：{@code appKey}（渠道侧应用标识，回调验签与配置反查用）、
 * {@code payOutcome}（下单结果模拟：success / fail / paying，默认 paying）。</p>
 *
 * @author lianghang
 */
public class MockParams implements ChannelParams {

    /** 下单即成功的模拟模式 */
    public static final String OUTCOME_SUCCESS = "success";
    /** 下单即失败的模拟模式 */
    public static final String OUTCOME_FAIL = "fail";
    /** 下单进入待支付、等待回调的模拟模式（默认） */
    public static final String OUTCOME_PAYING = "paying";

    private String appKey = "mock-app-key";
    private String payOutcome = OUTCOME_PAYING;

    public static MockParams fromMap(Map<String, String> properties) {
        MockParams params = new MockParams();
        if (properties != null) {
            if (properties.get("appKey") != null) {
                params.setAppKey(properties.get("appKey"));
            }
            if (properties.get("payOutcome") != null) {
                params.setPayOutcome(properties.get("payOutcome"));
            }
        }
        return params;
    }

    @Override
    public String channelAppId() {
        return appKey;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getPayOutcome() {
        return payOutcome;
    }

    public void setPayOutcome(String payOutcome) {
        this.payOutcome = payOutcome;
    }
}
