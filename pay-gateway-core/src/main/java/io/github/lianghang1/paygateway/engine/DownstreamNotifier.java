package io.github.lianghang1.paygateway.engine;

import io.github.lianghang1.paygateway.model.NotifyLog;

/**
 * 下游通知投递器：订单终态后向使用方业务系统发起 HTTP 通知并按策略重试。
 *
 * <p>通知记录先经 NotifyLogStore 落库再投递；{@code submit} 用于即时投递
 * （引擎内部在订单终态后调用），{@code dispatchDue} 供使用方定时任务兜底调用
 * （应用重启后恢复重试计划）。</p>
 *
 * @author lianghang
 */
public interface DownstreamNotifier {

    /** 登记后的通知记录尽快发起首次投递（异步，不阻塞调用线程）。 */
    void submit(NotifyLog notifyLog);

    /** 投递所有到期未完成的记录（建议由使用方定时任务每 10~15 秒调用一次）。 */
    void dispatchDue(int limit);
}
