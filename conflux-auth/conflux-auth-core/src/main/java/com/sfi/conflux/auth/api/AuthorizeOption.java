package com.sfi.conflux.auth.api;

import java.util.Map;

/**
 * 授权可选项：不预设字段，承载任何平台特有的授权参数与 state 透传上下文。
 *
 * <p>用 Map 而非强类型字段，正是为了参数名不写死。provider 需要往授权 URL 塞任何
 * 平台特有参数都从 {@link #extraParams} 取，门面不预设字段。</p>
 *
 * @author lianghang
 */
public class AuthorizeOption {

    /** 附加到授权请求的平台特有参数（如 display=popup、stylelite=1、app_id 别名…） */
    private Map<String, String> extraParams;

    /** 业务自定义上下文，写入 StateCache，回调时原样取回（如透传业务单号） */
    private Map<String, String> stateExtra;

    /** 覆盖本次 state 的过期秒数（可选） */
    private Integer expireSeconds;

    public Map<String, String> getExtraParams() {
        return extraParams;
    }

    public AuthorizeOption setExtraParams(Map<String, String> extraParams) {
        this.extraParams = extraParams;
        return this;
    }

    public Map<String, String> getStateExtra() {
        return stateExtra;
    }

    public AuthorizeOption setStateExtra(Map<String, String> stateExtra) {
        this.stateExtra = stateExtra;
        return this;
    }

    public Integer getExpireSeconds() {
        return expireSeconds;
    }

    public AuthorizeOption setExpireSeconds(Integer expireSeconds) {
        this.expireSeconds = expireSeconds;
        return this;
    }
}
