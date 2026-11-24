package com.sfi.conflux.platform.core;

/**
 * client 构造工厂：由各平台门面提供，负责把 {@link PlatformCredential} 建成一个原生 SDK client。
 *
 * @param <C> 原生 client 类型
 * @author lianghang
 */
@FunctionalInterface
public interface ClientFactory<C> {

    /** 构造一个 client 实例 */
    C create() throws Exception;
}
