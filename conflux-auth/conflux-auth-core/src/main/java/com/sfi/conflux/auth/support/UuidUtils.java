package com.sfi.conflux.auth.support;

import java.util.UUID;

/**
 * state 随机串工具：JDK8 原生 UUID，去掉连字符作紧凑串。
 *
 * @author lianghang
 */
public final class UuidUtils {

    private UuidUtils() {
    }

    /** 生成一个无连字符的随机串，用作 state */
    public static String random() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
