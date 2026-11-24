package com.sfi.conflux.notify.channels;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayObject;
import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;
import com.sfi.conflux.notify.api.NotificationMessage;
import com.sfi.conflux.notify.api.NotificationSendResult;
import com.sfi.conflux.notify.channel.AbstractNotificationChannel;
import com.sfi.conflux.notify.config.NotificationConfig;
import com.sfi.conflux.platform.alipay.AlipayClients;
import com.sfi.conflux.platform.core.PlatformCredential;

/**
 * 支付宝系通知渠道公共基类：复用通知域渠道模板（异常归一 + 上下文回填），统一 client 获取与
 * Request → execute 调用。支付宝 Request 是执行壳、业务参数在 Model（AlipayObject）里，
 * 故{@link #exec} 内部完成 setBizModel + execute + 响应归一，子类只管构造 Request。
 *
 * @author lianghang
 */
public abstract class AbstractAlipayChannel extends AbstractNotificationChannel {

    private final AlipayClients clients;
    /** 测试注入用：直接给定原生 client，跳过凭证映射与平台层，便于 mock。 */
    private final AlipayClient injectedClient;

    protected AbstractAlipayChannel(AlipayClients clients) {
        this.clients = clients;
        this.injectedClient = null;
    }

    protected AbstractAlipayChannel(AlipayClient injectedClient) {
        this.clients = null;
        this.injectedClient = injectedClient;
    }

    /** 按凭证 signMode 决定公钥模式 execute 还是证书模式 certificateExecute。 */
    @SuppressWarnings("unchecked")
    protected final <T extends AlipayResponse> T exec(NotificationConfig config,
                                                       AlipayRequest<T> request, AlipayObject model) throws Exception {
        request.setBizModel(model);
        AlipayClient client = client(config);
        boolean certMode = "cert".equalsIgnoreCase(attr(config, "signMode"))
                || "certificate".equalsIgnoreCase(attr(config, "signMode"));
        return certMode ? client.certificateExecute(request) : client.execute(request);
    }

    /** 响应 → 统一结果：success 取 SDK isSuccess，raw 放原始 response。 */
    protected final NotificationSendResult result(AlipayResponse response) {
        NotificationSendResult r = new NotificationSendResult();
        r.setRaw(response);
        r.setSuccess(response != null && response.isSuccess());
        return r;
    }

    private AlipayClient client(NotificationConfig config) {
        if (injectedClient != null) {
            return injectedClient;
        }
        return clients.client(PlatformCredential.of(
                attr(config, "appId"), attr(config, "privateKey"), config.getAttr()));
    }

    private static String attr(NotificationConfig config, String key) {
        return config.getAttr() == null ? null : config.getAttr().get(key);
    }
}
