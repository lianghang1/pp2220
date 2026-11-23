package com.sfi.conflux.auth.support;

import java.util.Map;

/**
 * state 缓存 SPI（零 Redis 依赖的核心契约）。
 *
 * <p>只存 state 与业务透传的 extra，不存 configId/channelCode——回调时 configId/channelCode
 * 是显式传参，不靠 state 定位。门面默认注入 {@link InMemoryStateCache}（单机内存）；
 * 集群部署由使用方实现本接口（Redis / DB / 分布式缓存）。门面层不 import 任何缓存客户端类。</p>
 *
 * @author lianghang
 */
public interface StateCache {

    /** 写入 state 及业务透传上下文，指定过期秒数 */
    void save(String state, Map<String, String> extra, int expireSeconds);

    /** 取出并删除（取出即删，天然防重放）；过期或未命中返回 null */
    Map<String, String> loadAndRemove(String state);
}
