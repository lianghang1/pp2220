package io.github.lianghang1.paygateway.event;

/**
 * 订单事件监听器 SPI：使用方实现并注册，接收订单状态迁移事件。
 *
 * <p>监听器异常被引擎隔离（记录日志、不影响主流程与其他监听器）；
 * 需要事务性语义（如事件与本地业务同库同事务）时，使用方可在自己的
 * Store 实现内自行处理。实现必须线程安全。</p>
 *
 * @author lianghang
 */
public interface PaymentEventListener {

    /** 处理订单事件。 */
    void onEvent(PaymentEvent event);
}
