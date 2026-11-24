package com.sfi.conflux.platform.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 平台层唯一共用的缓存工具：凭证指纹 → client 实例。
 *
 * <p>各平台门面内部复用本工具（不暴露复杂 SPI）。key 由「渠道键 + 凭证指纹」拼成：
 * 同一凭证重复取复用同一实例；凭证变更（指纹变化）自动落新 key、构造新实例，旧实例自然淘汰。
 * 用 computeIfAbsent 保证并发下同一指纹只构造一次（mapping 函数原子执行）、返回稳定的同一份实例。</p>
 *
 * <p>纯 Java，零 Spring / 零 SDK / 零 Redis——不知道 client 是什么类型，只做 Object 缓存。</p>
 *
 * @author lianghang
 */
public final class ClientCache {

    private final ConcurrentMap<String, Object> map = new ConcurrentHashMap<String, Object>();

    /**
     * 取指定 key + 指纹对应的 client，未命中则构造并缓存。
     *
     * @param key         渠道键（如 wechat:ma）
     * @param fingerprint 凭证指纹（如 appId|secret）
     * @param factory     client 构造工厂
     * @param <C>         client 类型
     * @return 缓存中或新建的 client 实例
     */
    @SuppressWarnings("unchecked")
    public <C> C get(String key, String fingerprint, ClientFactory<C> factory) {
        String k = key + ":" + fingerprint;
        return (C) map.computeIfAbsent(k, ignored -> {
            try {
                return factory.create();
            } catch (Exception e) {
                throw new IllegalStateException("构造 client 失败: " + k, e);
            }
        });
    }
}
