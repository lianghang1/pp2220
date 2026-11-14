package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.pay.model.NotifyLog;

/**
 * 空投递器：未注册通知存储时使用的降级实现，全部动作忽略。
 *
 * @author lianghang
 */
public final class NoopNotifier implements DownstreamNotifier {

    private static final NoopNotifier INSTANCE = new NoopNotifier();

    private NoopNotifier() {
    }

    /** 共享单例。 */
    public static NoopNotifier instance() {
        return INSTANCE;
    }

    @Override
    public void submit(NotifyLog notifyLog) {
        // 未注册通知存储，通知功能关闭
    }

    @Override
    public void dispatchDue(int limit) {
        // 未注册通知存储，通知功能关闭
    }
}
