package com.sfi.conflux.pay.channels.wechat;

import com.sfi.conflux.common.http.HttpResponse;
import com.sfi.conflux.common.http.HttpTransport;
import com.sfi.conflux.common.exception.CommonErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.common.util.StringUtils;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 微信 V2 协议直连客户端（WxJava 未覆盖的接口，如服务商付款码）：
 * XML 报文 + MD5 签名（参数 ASCII 排序、排除 sign/空值，末尾拼接
 * {@code &key=商户支付密钥}，转大写）。HTTP 传输走 common {@link HttpTransport}。
 * 全部方法线程安全。
 *
 * @author lianghang
 */
@Slf4j
public class WechatV2Client {

    private static final String PAY_BASE_URL = "https://api.mch.weixin.qq.com";
    private static final long CONNECT_MILLIS = 10000L;
    private static final long READ_MILLIS = 30000L;
    private static final long WRITE_MILLIS = 10000L;

    /**
     * V2 接口调用：组装 XML（自动补 nonce_str 与签名）→ POST → 解析应答 XML
     * 为键值 Map。校验 return_code / result_code 均为 SUCCESS，否则抛渠道异常。
     *
     * @param apiPath 接口路径（如 {@code /pay/micropay}）
     * @param fields  业务参数（不含 nonce_str/sign，签名自动计算）
     */
    public Map<String, String> execute(WechatParams params, String apiPath,
                                       Map<String, String> fields) {
        if (StringUtils.isBlank(params.getApiV2Key())) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "微信 V2 直连需要配置 apiV2Key");
        }
        Map<String, String> body = new TreeMap<String, String>(fields);
        body.put("nonce_str", UUID.randomUUID().toString().replace("-", ""));
        body.put("sign", v2Sign(body, params.getApiV2Key()));

        String url = PAY_BASE_URL + apiPath;
        StringBuilder xml = new StringBuilder("<xml>");
        for (Map.Entry<String, String> entry : body.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            xml.append('<').append(entry.getKey()).append("><![CDATA[")
                    .append(entry.getValue()).append("]]></").append(entry.getKey()).append('>');
        }
        xml.append("</xml>");

        try {
            HttpResponse response = HttpTransport.postBytes(url, null, HttpTransport.XML,
                    xml.toString().getBytes(StandardCharsets.UTF_8),
                    CONNECT_MILLIS, READ_MILLIS, WRITE_MILLIS);
            String text = response.getBody();
            log.info("微信 V2 直连完成: path={}, resp={}", apiPath, text);
            if (!response.isSuccessful() || StringUtils.isBlank(text)) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "微信 V2 直连异常: status={}, body={}", response.getStatusCode(), text);
            }
            Map<String, String> result = WechatXmlParser.parse(text);
            if (!"SUCCESS".equals(result.get("return_code"))) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "微信 V2 直连失败: return_msg={}", result.get("return_msg"));
            }
            if (!"SUCCESS".equals(result.get("result_code"))) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "微信 V2 业务失败: err_code={}, err_code_des={}",
                        result.get("err_code"), result.get("err_code_des"));
            }
            return result;
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信 V2 直连调用失败: {}", e.getMessage(), e);
        }
    }

    /** V2 签名：参数 ASCII 排序（排除 sign/空值）+ {@code &key=密钥} → MD5 → 大写。 */
    static String v2Sign(Map<String, String> params, String apiV2Key) {
        TreeMap<String, String> sorted = new TreeMap<String, String>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if ("sign".equals(entry.getKey()) || StringUtils.isBlank(entry.getValue())) {
                continue;
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('&');
        }
        sb.append("key=").append(apiV2Key);
        try {
            byte[] hash = MessageDigest.getInstance("MD5")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new GatewayException(CommonErrorCode.SYSTEM_ERROR, "MD5 摘要失败", e);
        }
    }
}
