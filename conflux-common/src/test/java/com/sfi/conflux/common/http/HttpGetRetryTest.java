package com.sfi.conflux.common.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 幂等 GET 重试回归：IOException/429/5xx 按指数退避重试，4xx 不重试；
 * 重试耗尽后可重试响应原样返回（成败判定归调用方），IO 异常抛最后一次。
 */
class HttpGetRetryTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** 前两次 503，第三次 200：重试后拿到成功响应。 */
    @Test
    void retriesServerErrorsUntilSuccess() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/flaky", exchange -> {
            int n = hits.incrementAndGet();
            if (n < 3) {
                exchange.getResponseHeaders().set("Connection", "close");
                exchange.sendResponseHeaders(503, -1);
            } else {
                byte[] data = "ok".getBytes();
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            }
        });

        HttpResponse resp = HttpTransport.getWithRetry(base + "/flaky", null,
                2000L, 2000L, 2000L, 3, 10L);
        assertThat(resp.isSuccessful()).isTrue();
        assertThat(resp.getBody()).isEqualTo("ok");
        assertThat(hits.get()).isEqualTo(3);
    }

    /** 429 限流同样触发重试。 */
    @Test
    void retriesOn429() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/limited", exchange -> {
            int n = hits.incrementAndGet();
            if (n < 2) {
                exchange.getResponseHeaders().set("Connection", "close");
                exchange.sendResponseHeaders(429, -1);
            } else {
                byte[] data = "ok".getBytes();
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            }
        });

        HttpResponse resp = HttpTransport.getWithRetry(base + "/limited", null,
                2000L, 2000L, 2000L, 2, 10L);
        assertThat(resp.isSuccessful()).isTrue();
        assertThat(hits.get()).isEqualTo(2);
    }

    /** 404 属确定失败：不重试，只打一次。 */
    @Test
    void doesNotRetry4xx() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/gone", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
        });

        HttpResponse resp = HttpTransport.getWithRetry(base + "/gone", null,
                2000L, 2000L, 2000L, 2, 10L);
        assertThat(resp.getStatusCode()).isEqualTo(404);
        assertThat(hits.get()).isEqualTo(1);
    }

    /** 重试耗尽仍 5xx：原样返回最后一次响应，不抛异常。 */
    @Test
    void returnsLastRetryableResponseWhenExhausted() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/always503", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().set("Connection", "close");
            exchange.sendResponseHeaders(503, -1);
        });

        HttpResponse resp = HttpTransport.getWithRetry(base + "/always503", null,
                2000L, 2000L, 2000L, 2, 10L);
        assertThat(resp.getStatusCode()).isEqualTo(503);
        assertThat(hits.get()).isEqualTo(3);
    }

    /** 连接拒绝（服务未监听端口）：重试耗尽后抛最后一次 IO 异常。 */
    @Test
    void throwsLastIoErrorWhenExhausted() {
        int port = server.getAddress().getPort();
        server.stop(0);
        assertThatThrownBy(() -> HttpTransport.getWithRetry("http://127.0.0.1:" + port + "/x", null,
                500L, 500L, 500L, 2, 10L))
                .isInstanceOf(HttpTransportException.class);
    }
}
