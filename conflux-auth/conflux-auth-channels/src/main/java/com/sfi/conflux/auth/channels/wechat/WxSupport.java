package com.sfi.conflux.auth.channels.wechat;

import com.sfi.conflux.auth.config.AuthConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 微信/企微 provider 公共小工具：URL 编码、附加参数、性别归一、agentId 读取。
 *
 * <p>只做无状态字符串处理，不涉及任何网络/加密脏活（那些一律委托 wxjava）。</p>
 *
 * @author lianghang
 */
final class WxSupport {

    private WxSupport() {
    }

    /** URL 编码（授权 URL 的 redirect_uri 等必须编码）。UTF-8 在 JDK8 必然可用。 */
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

    /** 微信 sex：1=男 2=女 0/其它=未知 */
    static String genderOf(Integer sex) {
        if (sex == null) {
            return null;
        }
        if (sex == 1) {
            return "M";
        }
        if (sex == 2) {
            return "F";
        }
        return "UNKNOWN";
    }

    /** 企微 userDetail.gender 是字符串 "1"/"2" */
    static String genderOf(String sex) {
        if (sex == null) {
            return null;
        }
        String t = sex.trim();
        if ("1".equals(t)) {
            return "M";
        }
        if ("2".equals(t)) {
            return "F";
        }
        return "UNKNOWN";
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

    /** 企微 agentId 从 attr 读取（agentId / agentid 两种写法兼容），无则 null */
    static Integer agentId(AuthConfig config) {
        Map<String, String> attr = config.getAttr();
        if (attr == null) {
            return null;
        }
        String v = attr.get("agentId");
        if (v == null) {
            v = attr.get("agentid");
        }
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
}
