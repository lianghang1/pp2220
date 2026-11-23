package com.sfi.conflux.auth.config;

import java.util.Collections;
import java.util.Map;

/**
 * 登录配置源 SPI：门面每次按 configId + channelCode 取配置。
 *
 * <p>Boot 形态由 starter 注入读 yml 的实现；MVC / 纯 Java 由业务实现本接口，
 * 从 DB / 配置中心 / 硬编码任意来源取配置。构建逻辑三种形态共用一份。</p>
 *
 * @author lianghang
 */
public interface AuthConfigProvider {

    /** 按 configId + channelCode 取配置（与四域 getConfig 契约同名） */
    AuthConfig getConfig(String configId, String channelCode);

    /**
     * 全部配置（可选覆盖）：返回空表示不预热。
     * 实现方可在构建时用于 fail-fast 校验——与 sms/pay 域的 allConfigs 契约对齐。
     */
    default Map<String, AuthConfig> allConfigs() {
        return Collections.emptyMap();
    }
}
