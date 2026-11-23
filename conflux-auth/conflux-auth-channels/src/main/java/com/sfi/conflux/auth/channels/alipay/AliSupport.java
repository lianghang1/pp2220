package com.sfi.conflux.auth.channels.alipay;

import com.sfi.conflux.auth.api.AuthorizeOption;
import com.sfi.conflux.auth.config.AuthConfig;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 支付宝渠道公共小工具：URL 编码、附加参数、attr 读取、网关/签名模式选择、性别归一。
 *
 * <p>支付宝不是标准 RESTful OAuth，而是 {@code gateway.do + method} 网关协议，
 * 签名 RSA2（公钥 / 证书两种模式）全部交给官方 alipay-sdk-java，本类只做无状态字符串处理。</p>
 *
 * <p><b>参数不写死</b>：网关、签名模式、证书路径、支付宝公钥均从 {@code attr/extraParams} 读取；
 * 应用私钥沿用公共字段 {@code clientSecret}（与 JustAuth 的 clientSecret=privateKey 一致）。</p>
 *
 * @author lianghang
 */
final class AliSupport {

    /** 授权页（浏览器跳转 / 唤起支付宝客户端） */
    static final String AUTHORIZE_URL = "https://openauth.alipay.com/oauth2/publicAppAuthorize.htm";
    /** 默认网关（生产），可用 attr.gateway 覆盖（如沙箱） */
    static final String DEFAULT_GATEWAY = "https://openapi.alipay.com/gateway.do";

    private AliSupport() {
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

    /** 把平台特有附加参数追加到授权 URL，不写死参数名 */
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
        return url + (url.indexOf('?') < 0 ? '?' : '&') + query;
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

    /** 网关：attr.gateway 覆盖，默认生产 */
    static String gateway(AuthConfig config) {
        String gw = param(config, null, "gateway");
        return (gw == null || gw.trim().isEmpty()) ? DEFAULT_GATEWAY : gw.trim();
    }

    /** 是否证书模式：attr.signMode=cert（否则公钥模式） */
    static boolean certificateMode(AuthConfig config) {
        String signMode = param(config, null, "signMode");
        return "cert".equalsIgnoreCase(signMode) || "certificate".equalsIgnoreCase(signMode);
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

    /** 支付宝性别 "M"/"F" 归一，取不到返回 null（不否决身份） */
    static String genderOf(String gender) {
        if (gender == null) {
            return null;
        }
        String t = gender.trim();
        if ("M".equalsIgnoreCase(t) || "1".equals(t) || "male".equalsIgnoreCase(t)) {
            return "M";
        }
        if ("F".equalsIgnoreCase(t) || "0".equals(t) || "female".equalsIgnoreCase(t)) {
            return "F";
        }
        return null;
    }
}
