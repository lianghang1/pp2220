package io.github.lianghang1.conflux.pay.engine;

import java.util.concurrent.TimeUnit;

/**
 * 下游通知重试策略：分级退避间隔。
 *
 * <p>默认节奏：15s / 15s / 30s / 3m / 10m / 20m / 30m / 30m / 30m / 30m / 1h / 1h，
 * 共 12 次尝试；第 n 次失败后的等待间隔取 {@code delays[n-1]}（越界取最后一档）。</p>
 *
 * @author lianghang
 */
public final class RetryPolicy {

    /** 默认重试间隔（秒）。 */
    public static final int[] DEFAULT_DELAYS_SECONDS = {
            15, 15, 30, 180, 600, 1200, 1800, 1800, 1800, 1800, 3600, 3600
    };

    private final long[] delayMillis;

    /** 以秒级间隔数组构建策略；空数组视为不重试（仅首次投递）。 */
    public RetryPolicy(int[] delaysSeconds) {
        if (delaysSeconds == null || delaysSeconds.length == 0) {
            this.delayMillis = new long[0];
            return;
        }
        this.delayMillis = new long[delaysSeconds.length];
        for (int i = 0; i < delaysSeconds.length; i++) {
            this.delayMillis[i] = TimeUnit.SECONDS.toMillis(delaysSeconds[i]);
        }
    }

    /** 最大尝试次数（含首次投递）。 */
    public int getMaxAttempts() {
        return delayMillis.length;
    }

    /** 第 attemptIndex 次失败后的等待毫秒数（attemptIndex 从 1 计，越界取最后一档）。 */
    public long delayMillis(int attemptIndex) {
        if (delayMillis.length == 0) {
            return 0L;
        }
        int idx = Math.max(0, Math.min(attemptIndex - 1, delayMillis.length - 1));
        return delayMillis[idx];
    }
}
