package io.github.lianghang1.conflux.pay.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关 starter 配置（{@code conflux.pay.*}）。
 *
 * <p>渠道参数以「应用 → 渠道 → 键值对」结构声明，键名约定由各渠道定义
 * （如 mock 渠道的 appKey/payOutcome）。下游通知配置控制 HTTP 投递行为。</p>
 *
 * @author lianghang
 */
@ConfigurationProperties(prefix = "conflux.pay")
public class PayGatewayProperties {

    /** 应用 → 渠道配置 */
    private Map<String, AppProperties> apps = new LinkedHashMap<String, AppProperties>();

    /** 下游通知配置 */
    private Notify notify = new Notify();

    public Map<String, AppProperties> getApps() {
        return apps;
    }

    public void setApps(Map<String, AppProperties> apps) {
        this.apps = apps;
    }

    public Notify getNotify() {
        return notify;
    }

    public void setNotify(Notify notify) {
        this.notify = notify;
    }

    /**
     * 单应用的渠道配置集合：统一键（notifyUrl/sandbox/subMchNo）+ attrs 渠道个性键值包。
     */
    public static class AppProperties {

        /** 渠道编码 → 渠道配置 */
        private Map<String, ChannelProperties> channels = new LinkedHashMap<String, ChannelProperties>();

        public Map<String, ChannelProperties> getChannels() {
            return channels;
        }

        public void setChannels(Map<String, ChannelProperties> channels) {
            this.channels = channels;
        }
    }

    /**
     * 单渠道配置：统一基础键 + attrs 渠道个性键值包。
     */
    public static class ChannelProperties {

        /** 渠道异步通知地址（统一键） */
        private String notifyUrl;
        /** 沙箱开关（统一键，有官方沙箱的渠道据此切换网关） */
        private boolean sandbox;
        /** 服务商模式特约商户号（统一键，微信/云闪付等子商户场景） */
        private String subMchNo;
        /** 渠道个性键值包（键名由各渠道定义，经 ChannelProvider.parseParams 解析为渠道参数对象） */
        private Map<String, String> attrs = new LinkedHashMap<String, String>();

        public String getNotifyUrl() {
            return notifyUrl;
        }

        public void setNotifyUrl(String notifyUrl) {
            this.notifyUrl = notifyUrl;
        }

        public boolean isSandbox() {
            return sandbox;
        }

        public void setSandbox(boolean sandbox) {
            this.sandbox = sandbox;
        }

        public String getSubMchNo() {
            return subMchNo;
        }

        public void setSubMchNo(String subMchNo) {
            this.subMchNo = subMchNo;
        }

        public Map<String, String> getAttrs() {
            return attrs;
        }

        public void setAttrs(Map<String, String> attrs) {
            this.attrs = attrs;
        }
    }

    /**
     * 下游通知投递配置。
     */
    public static class Notify {

        /** 重试间隔（秒），空取内置默认节奏 */
        private List<Integer> retrySeconds = new ArrayList<Integer>();
        /** 通知签名密钥（空表示不签名） */
        private String secret;
        /** 单次投递超时（毫秒） */
        private long timeoutMillis = 10000L;

        public List<Integer> getRetrySeconds() {
            return retrySeconds;
        }

        public void setRetrySeconds(List<Integer> retrySeconds) {
            this.retrySeconds = retrySeconds;
        }

        public int[] retrySecondsArray() {
            return retrySeconds == null || retrySeconds.isEmpty()
                    ? io.github.lianghang1.conflux.pay.engine.RetryPolicy.DEFAULT_DELAYS_SECONDS
                    : toIntArray(retrySeconds);
        }

        private int[] toIntArray(List<Integer> values) {
            int[] array = new int[values.size()];
            for (int i = 0; i < values.size(); i++) {
                array[i] = values.get(i);
            }
            return array;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }
    }
}
