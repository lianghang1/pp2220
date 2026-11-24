package com.sfi.conflux.platform.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClientCache 单元测试：指纹命中、凭证变更重建、并发单实例、异常封装。
 *
 * @author lianghang
 */
class ClientCacheTest {

    @Test
    void sameFingerprintReusesInstance() {
        ClientCache cache = new ClientCache();
        AtomicInteger builds = new AtomicInteger();
        ClientFactory<Object> factory = () -> {
            builds.incrementAndGet();
            return new Object();
        };

        Object a = cache.get("wechat:ma", "app1|sec1", factory);
        Object b = cache.get("wechat:ma", "app1|sec1", factory);

        assertSame(a, b);
        assertEquals(1, builds.get());
    }

    @Test
    void changedFingerprintRebuilds() {
        ClientCache cache = new ClientCache();
        ClientFactory<Object> factory = Object::new;

        Object a = cache.get("wechat:ma", "app1|sec1", factory);
        Object b = cache.get("wechat:ma", "app1|sec2", factory);

        assertNotSame(a, b);
        // 旧指纹仍在缓存中，再取仍是原实例
        assertSame(a, cache.get("wechat:ma", "app1|sec1", factory));
    }

    @Test
    void differentKeysDoNotCollide() {
        ClientCache cache = new ClientCache();
        ClientFactory<Object> factory = Object::new;

        Object ma = cache.get("wechat:ma", "app1|sec1", factory);
        Object mp = cache.get("wechat:mp", "app1|sec1", factory);

        assertNotSame(ma, mp);
    }

    @Test
    void concurrentGetBuildsOnce() throws InterruptedException {
        ClientCache cache = new ClientCache();
        AtomicInteger builds = new AtomicInteger();
        ClientFactory<Object> factory = () -> {
            builds.incrementAndGet();
            Thread.sleep(20);
            return new Object();
        };
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Object[] results = new Object[threads];

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    start.await();
                    results[idx] = cache.get("wechat:ma", "app|sec", factory);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertEquals(1, builds.get());
        for (Object r : results) {
            assertSame(results[0], r);
        }
    }

    @Test
    void factoryExceptionIsWrapped() {
        ClientCache cache = new ClientCache();
        ClientFactory<Object> factory = () -> {
            throw new RuntimeException("boom");
        };
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cache.get("wechat:ma", "app|sec", factory));
        assertTrue(ex.getMessage().contains("wechat:ma"));
    }
}
