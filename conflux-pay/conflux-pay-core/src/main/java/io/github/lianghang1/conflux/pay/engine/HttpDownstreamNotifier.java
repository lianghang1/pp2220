package io.github.lianghang1.conflux.pay.engine;

import io.github.lianghang1.conflux.common.http.HttpResponse;
import io.github.lianghang1.conflux.common.http.HttpTransport;
import io.github.lianghang1.conflux.common.util.SignUtil;
import io.github.lianghang1.conflux.pay.model.NotifyLog;
import io.github.lianghang1.conflux.pay.store.NotifyLogStore;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 HTTP 的下游通知投递器：单线程调度、分级退避重试、可选 HMAC 签名头。
 *
 * <p>投递成功判定为 HTTP 2xx；失败后按 {@link RetryPolicy} 安排下一次投递，
 * 超出上限后终结重试计划（completed=true，success=false）。通知体取自
 * 通知记录中固化的 {@code requestContent}，重试投递同一内容。
 * HTTP 传输统一走 common 的 {@link HttpTransport}（超时由构造参数运行时配置）。
 * 单实例内部单线程串行调度，同一记录可能因 submit 与 dispatchDue 并发产生
 * 重复投递——通知应答方需按 orderId 幂等处理。</p>
 *
 * @author lianghang
 */
@Slf4j
public class HttpDownstreamNotifier implements DownstreamNotifier {

    /** 签名请求头名（配置通知密钥后自动携带，值为请求体的 HMAC-SHA256 hex）。 */
    public static final String SIGNATURE_HEADER = "X-Gateway-Signature";

    private static final int RESPONSE_PREVIEW_LENGTH = 256;

    private final NotifyLogStore store;
    private final RetryPolicy retryPolicy;
    private final String secret;
    private final long timeoutMillis;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    /**
     * @param store         通知记录存储（必填）
     * @param retryPolicy   重试策略
     * @param secret        通知签名密钥（null 表示不签名）
     * @param timeoutMillis 单次投递连接/读/写超时（毫秒）
     */
    public HttpDownstreamNotifier(NotifyLogStore store, RetryPolicy retryPolicy,
                                  String secret, long timeoutMillis, Clock clock) {
        this.store = store;
        this.retryPolicy = retryPolicy;
        this.secret = secret;
        this.timeoutMillis = timeoutMillis;
        this.clock = clock;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "conflux-pay-notify-" + seq.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public void submit(NotifyLog notifyLog) {
        scheduleAttempt(notifyLog, 0L);
    }

    @Override
    public void dispatchDue(int limit) {
        List<NotifyLog> dueLogs = store.findDueNotifies(LocalDateTime.now(clock), limit);
        for (NotifyLog notifyLog : dueLogs) {
            scheduleAttempt(notifyLog, 0L);
        }
    }

    private void scheduleAttempt(NotifyLog notifyLog, long delayMillis) {
        scheduler.schedule(() -> attempt(notifyLog), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void attempt(NotifyLog notifyLog) {
        try {
            // requestContent 为入队时固化的 JSON 报文，直接按原文发送，不再二次序列化
            Map<String, String> headers = new LinkedHashMap<String, String>();
            if (secret != null) {
                headers.put(SIGNATURE_HEADER,
                        SignUtil.hmacSha256Hex(notifyLog.getRequestContent(), secret));
            }
            HttpResponse response = HttpTransport.postText(notifyLog.getNotifyUrl(), headers,
                    HttpTransport.JSON, notifyLog.getRequestContent(),
                    timeoutMillis, timeoutMillis, timeoutMillis);
            boolean success = response.isSuccessful();
            String preview = truncate(response.getBody());
            settle(notifyLog, success, preview);
        } catch (Exception e) {
            log.warn("下游通知投递异常: notifyId={}, orderId={}, error={}",
                    notifyLog.getNotifyId(), notifyLog.getOrderId(), e.getMessage());
            settle(notifyLog, false, truncate(e.getMessage()));
        }
    }

    /** 按投递结果更新台账并安排下一步（成功终结 / 失败重试 / 超限终结）。 */
    private void settle(NotifyLog notifyLog, boolean success, String responsePreview) {
        try {
            notifyLog.setNotifyTimes(safeTimes(notifyLog) + 1);
            notifyLog.setSuccess(success);
            notifyLog.setResponseContent(responsePreview);
            notifyLog.setUpdatedTime(LocalDateTime.now(clock));
            if (success) {
                notifyLog.setCompleted(true);
                notifyLog.setNextNotifyTime(null);
                store.update(notifyLog);
                log.info("下游通知成功: notifyId={}, orderId={}, times={}",
                        notifyLog.getNotifyId(), notifyLog.getOrderId(), notifyLog.getNotifyTimes());
                return;
            }
            int times = notifyLog.getNotifyTimes();
            if (times >= retryPolicy.getMaxAttempts()) {
                notifyLog.setCompleted(true);
                store.update(notifyLog);
                log.warn("下游通知重试超限，计划终结: notifyId={}, orderId={}, times={}",
                        notifyLog.getNotifyId(), notifyLog.getOrderId(), times);
                return;
            }
            long delay = retryPolicy.delayMillis(times);
            notifyLog.setNextNotifyTime(LocalDateTime.now(clock).plusNanos(TimeUnit.MILLISECONDS.toNanos(delay)));
            store.update(notifyLog);
            scheduleAttempt(notifyLog, delay);
        } catch (Exception e) {
            log.error("下游通知台账更新失败: notifyId={}", notifyLog.getNotifyId(), e);
        }
    }

    private int safeTimes(NotifyLog notifyLog) {
        return notifyLog.getNotifyTimes() == null ? 0 : notifyLog.getNotifyTimes();
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= RESPONSE_PREVIEW_LENGTH ? text : text.substring(0, RESPONSE_PREVIEW_LENGTH);
    }
}
