package com.sfi.conflux.auth.channels.dingtalk;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.config.AuthConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 钉钉渠道公共小工具：URL 编码、附加参数、attr 读取、性别归一。
 *
 * <p>只做无状态字符串处理；JSON 解析统一用 conflux-common 的 JsonUtils，HTTP 用 DingHttpClient。</p>
 *
 * @author lianghang
 */
final class DingSupport {

    private DingSupport() {
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

    /** 取第一个非空字符串 */
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

    /** 钉钉性别：M/F/未知 */
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
