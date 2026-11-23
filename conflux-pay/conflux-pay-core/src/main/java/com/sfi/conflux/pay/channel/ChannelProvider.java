package com.sfi.conflux.pay.channel;

import java.util.Set;

/**
 * 渠道提供者 SPI：一个渠道实现的统一入口。
 *
 * <p>实现方声明渠道编码与支持的支付/转账方式，并按渠道能力选择性暴露
 * 各能力处理器（不支持的能力返回 null，引擎按能力查找、缺失时报明确异常）。
 * 注册到 {@link ChannelRegistry} 后即可被引擎路由。</p>
 *
 * <p>实现要求：实例无状态、线程安全（所有方法可能被并发调用）；
 * 渠道专属 SDK 依赖只出现在渠道包内部。</p>
 *
 * @author lianghang
 */
public interface ChannelProvider {

    /** 渠道编码（全局唯一，如 "wechat"/自定义间联编码）。 */
    String channelCode();

    /** 支持的支付/转账方式编码集合（PayMethod.code）。 */
    Set<String> supportedMethods();

    /**
     * 将配置键值对解析为渠道参数对象（starter 默认配置实现按本方法构建渠道强类型 config）。
     * 键名约定由渠道包自行定义并在文档中说明；不支持声明式配置的渠道返回 null
     * （此时使用方必须自行实现 ChannelConfigProvider）。
     */
    default ChannelConfig parseParams(java.util.Map<String, String> properties) {
        return null;
    }

    /** 支付能力（下单/查单/关单）；不支持返回 null。 */
    default PaymentHandler paymentHandler() {
        return null;
    }

    /** 退款能力；不支持返回 null。 */
    default RefundHandler refundHandler() {
        return null;
    }

    /** 转账能力；不支持返回 null。 */
    default TransferHandler transferHandler() {
        return null;
    }

    /** 分账能力；不支持返回 null。 */
    default ProfitShareHandler profitShareHandler() {
        return null;
    }

    /** 账单能力（对账用）；不支持返回 null。 */
    default BillHandler billHandler() {
        return null;
    }

    /** 回调解析能力（验签/解析/应答报文）；不支持返回 null。 */
    default ChannelCallbackHandler callbackHandler() {
        return null;
    }

    /** 渠道用户标识能力（JSAPI 下单前获取 openid/buyerUserId）；不支持返回 null。 */
    default ChannelUserHandler channelUserHandler() {
        return null;
    }
}
