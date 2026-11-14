package io.github.lianghang1.conflux.pay;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lianghang1.conflux.pay.engine.HttpDownstreamNotifier;
import io.github.lianghang1.conflux.pay.engine.RetryPolicy;
import io.github.lianghang1.conflux.pay.model.NotifyLog;
import io.github.lianghang1.conflux.pay.support.InMemoryStores.NotifyLogStoreImpl;
import io.github.lianghang1.conflux.common.util.SignUtil;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 下游通知投递器测试：HTTP 投递、签名头、失败重试与终结。
 *
 * @author lianghang
 */
class NotifyDeliveryTest {

    private static final String SECRET = "notify-secret";

    private NotifyLog buildLog(String notifyUrl, String content) {
        return new NotifyLog()
                .setNotifyId("NTF-TEST")
                .setAppId("app-001")
                .setOrderId("PAY-TEST")
                .setBizOrderNo("NO-TEST")
                .setNotifyUrl(notifyUrl)
                .setNotifyTimes(0)
                .setSuccess(false)
                .setCompleted(false)
                .setRequestContent(content);
    }

    @Test
    void successfulDeliveryShouldCompleteImmediately() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<String>();
        AtomicReference<String> receivedSign = new AtomicReference<String>();
        HttpServer server = startServer(exchange -> {
            java.io.InputStream in = exchange.getRequestBody();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, n);
            }
            receivedBody.set(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            receivedSign.set(exchange.getRequestHeaders().getFirst(HttpDownstreamNotifier.SIGNATURE_HEADER));
            respond(exchange, 200, "success");
        });
        try {
            NotifyLogStoreImpl store = new NotifyLogStoreImpl();
            HttpDownstreamNotifier notifier = new HttpDownstreamNotifier(store,
                    new RetryPolicy(new int[]{1}), SECRET, 3000, Clock.systemDefaultZone());
            String content = "{\"orderId\":\"PAY-TEST\"}";
            NotifyLog notifyLog = buildLog("http://127.0.0.1:" + server.getAddress().getPort() + "/notify", content);
            store.save(notifyLog);
            notifier.submit(notifyLog);

            awaitCondition(() -> Boolean.TRUE.equals(store.all().get(0).getCompleted()), 5);
            NotifyLog settled = store.all().get(0);
            assertThat(settled.getSuccess()).isTrue();
            assertThat(settled.getNotifyTimes()).isEqualTo(1);
            assertThat(receivedBody.get()).isEqualTo(content);
            assertThat(receivedSign.get())
                    .isEqualTo(SignUtil.hmacSha256Hex(content, SECRET));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedDeliveryShouldRetryThenExhaust() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = startServer(exchange -> {
            hits.incrementAndGet();
            respond(exchange, 500, "error");
        });
        try {
            NotifyLogStoreImpl store = new NotifyLogStoreImpl();
            // 两档 1 秒重试：最大尝试次数 = 间隔档数 = 2
            HttpDownstreamNotifier notifier = new HttpDownstreamNotifier(store,
                    new RetryPolicy(new int[]{1, 1}), null, 3000, Clock.systemDefaultZone());
            NotifyLog notifyLog = buildLog("http://127.0.0.1:" + server.getAddress().getPort() + "/notify", "{}");
            store.save(notifyLog);
            notifier.submit(notifyLog);

            awaitCondition(() -> Boolean.TRUE.equals(store.all().get(0).getCompleted()), 10);
            NotifyLog settled = store.all().get(0);
            assertThat(settled.getSuccess()).isFalse();
            assertThat(settled.getNotifyTimes()).isEqualTo(2);
            assertThat(hits.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retryPolicyShouldClampDelayIndex() {
        RetryPolicy policy = new RetryPolicy(new int[]{15, 30, 60});
        assertThat(policy.getMaxAttempts()).isEqualTo(3);
        assertThat(policy.delayMillis(1)).isEqualTo(15000);
        assertThat(policy.delayMillis(2)).isEqualTo(30000);
        assertThat(policy.delayMillis(3)).isEqualTo(60000);
        assertThat(policy.delayMillis(99)).isEqualTo(60000);
    }

    private HttpServer startServer(HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/notify", handler);
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void awaitCondition(java.util.function.BooleanSupplier condition, int timeoutSeconds)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            while (!condition.getAsBoolean()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            latch.countDown();
        });
        waiter.setDaemon(true);
        waiter.start();
        assertThat(latch.await(timeoutSeconds, TimeUnit.SECONDS))
                .as("等待通知投递结果超时")
                .isTrue();
    }
}
