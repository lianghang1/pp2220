package io.github.lianghang1.paygateway.channel;

import io.github.lianghang1.paygateway.channel.ChannelConfig;

import java.util.Map;

/**
 * 渠道用户标识处理器契约：JSAPI 类支付方式下单前需要先拿到付款用户的
 * 渠道侧标识（微信 openid / 支付宝 buyerUserId），本 SPI 提供授权跳转
 * 与换取标识两个环节，供使用方自行编排页面流程。
 *
 * <p>实现要求：无状态、线程安全；渠道未提供时由使用方自行获取
 * （通过下单的 channelExtras 传入标识）。</p>
 *
 * @author lianghang
 */
public interface ChannelUserHandler {

    /**
     * 构建渠道授权跳转地址（引导用户在渠道内授权）。
     *
     * @param config     渠道配置
     * @param redirectUri 授权完成后渠道回跳的使用方地址（授权码随查询参数带回）
     * @param state      透传状态（防 CSRF，原样带回）
     */
    String buildUserRedirectUrl(ChannelConfig config, String redirectUri, String state);

    /**
     * 以渠道回跳参数（授权码等）换取渠道用户标识。
     *
     * @param config         渠道配置
     * @param callbackParams 渠道回跳时携带的参数（如授权码 code）
     * @return 渠道用户标识（微信 openid / 支付宝 buyerUserId）
     */
    String getChannelUserId(ChannelConfig config, Map<String, String> callbackParams);
}
