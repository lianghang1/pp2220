package com.sfi.conflux.auth.channels.feishu;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.config.AuthConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 飞书渠道公共小工具：URL 编码、附加参数、attr 读取、端点版本选择、性别归一。
 *
 * <p>只做无状态字符串处理；JSON 解析统一用 conflux-common 的 JsonUtils，HTTP 用 FeishuHttpClient。</p>
 *
 * <p><b>端点版本策略</b>：token 端点默认走官方推荐的 v3（{@code accounts.feishu.cn/oauth/v3/token}），
 * 使用方可通过 {@code attr/tokenVersion=v2} 或 {@code attr/tokenUrl=<完整地址>} 切换/覆盖，
 * 框架不写死版本、不写死 URL。</p>
 *
 * @author lianghang
 */
final class FeishuSupport {

    /** 授权页（OAuth authorize） */
    static final String AUTHORIZE_URL = "https://accounts.feishu.cn/open-apis/authen/v1/authorize";
    /** 用户信息（v1 user_info 仍为当前端点） */
    static final String USERINFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";
    /** 换取 user_access_token：v3 官方推荐 */
    static final String TOKEN_URL_V3 = "https://accounts.feishu.cn/oauth/v3/token";
    /** 换取 user_access_token：v2 历史版本（可配置降级） */
    static final String TOKEN_URL_V2 = "https://open.feishu.cn/open-apis/authen/v2/oauth/token";
    /** 应用 app_access_token（自建应用内部接口，小程序码校验用） */
    static final String APP_TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";
    /** 小程序 / 小组件 code2session（独立于 OAuth 授权码链路） */
    static final String MINI_TOKEN_URL = "https://open.feishu.cn/open-apis/mina/v2/tokenLoginValidate";

    private FeishuSupport() {
    }

    static String enc(String s) {
        if (s == null) {
            return "";
        }
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    /** 把平台特有附加参数追加到授权 URL，插在 #fragment 之前，不写死参数名 */
    static String appendExtra(String url, Map<String, String> extra) {
        if (extra == null || extra.isEmpty()) {
            return url;
        }
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : extra.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(e.getKey()).append('=').append(e.getValue());
            }
        }
        if (query.length() == 0) {
            return url;
        }
        int hash = url.indexOf('#');
        if (hash < 0) {
            return url + (url.indexOf('?') < 0 ? '?' : '&') + query;
        }
        String before = url.substring(0, hash);
        String fragment = url.substring(hash);
        return before + (before.indexOf('?') < 0 ? '?' : '&') + query + fragment;
    }

    /** 从 extraParams 或 attr 读平台特有参数（调用方不写死位置，两处都找） */
    static String param(AuthConfig config, AuthorizeOption option, String key) {
        if (option != null && option.getExtraParams() != null) {
            String v = option.getExtraParams().get(key);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        Map<String, String> attr = config.getAttr();
        return attr == null ? null : attr.get(key);
    }

    /**
     * token 端点：优先完整 tokenUrl 覆盖；否则按 tokenVersion 选 v2/v3，默认 v3。
     */
    static String tokenUrl(AuthConfig config, AuthorizeOption option) {
        String url = param(config, option, "tokenUrl");
        if (url != null && !url.trim().isEmpty()) {
            return url.trim();
        }
        String version = param(config, option, "tokenVersion");
        if ("v2".equalsIgnoreCase(version) || "2".equals(version)) {
            return TOKEN_URL_V2;
        }
        return TOKEN_URL_V3;
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }

    /**
     * 飞书性别归一：兼容老接口返回的 "1"/"2"/"male"/"female"。
     * 新版 user_info 不返回该字段，取不到时返回 null（不否决身份）。
     */
    static String genderOf(Object sex) {
        if (sex == null) {
            return null;
        }
        String t = String.valueOf(sex).trim();
        if ("1".equals(t) || "M".equalsIgnoreCase(t) || "male".equalsIgnoreCase(t)) {
            return "M";
        }
        if ("2".equals(t) || "F".equalsIgnoreCase(t) || "female".equalsIgnoreCase(t)) {
            return "F";
        }
        return "UNKNOWN";
    }
}
