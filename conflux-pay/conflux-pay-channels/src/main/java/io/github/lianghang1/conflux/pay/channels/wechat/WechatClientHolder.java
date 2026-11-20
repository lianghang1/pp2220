package io.github.lianghang1.conflux.pay.channels.wechat;

import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信支付客户端持有器：按商户凭证缓存 WxPayService（V2/V3 双协议由 WxJava
 * 统一封装，凭配置路由）。
 *
 * <p>同一商户凭证（WechatParams equals 语义）全局复用同一份服务实例；
 * 全部方法线程安全，WxPayService 实例自身线程安全可并发调用。</p>
 *
 * @author lianghang
 */
public class WechatClientHolder {

    private final ConcurrentHashMap<WechatParams, WxPayService> services =
            new ConcurrentHashMap<WechatParams, WxPayService>();

    /** 取指定配置对应的 WxPayService（按需构建并缓存）。 */
    public WxPayService service(ChannelConfig config) {
        WechatParams params = paramsOf(config);
        return services.computeIfAbsent(params, key -> buildService(key, config));
    }

    public static WechatParams paramsOf(ChannelConfig config) {
        if (config instanceof WechatParams) {
            return (WechatParams) config;
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "微信渠道参数类型不匹配: {}", config == null ? "null" : config.getClass().getName());
    }

    private WxPayService buildService(WechatParams params, ChannelConfig config) {
        WxPayConfig wxConfig = new WxPayConfig();
        wxConfig.setAppId(params.getAppId());
        wxConfig.setMchId(params.getMerchantId());
        wxConfig.setNotifyUrl(config.getNotifyUrl());
        // V2 仿真系统开关（V3 无官方沙箱）
        wxConfig.setUseSandboxEnv(config.isSandbox());
        // V2 协议
        wxConfig.setMchKey(params.getApiV2Key());
        wxConfig.setEntPayKey(params.effectiveEntPayKey());
        wxConfig.setSignType("MD5");
        // 服务商模式（特约商户号/子应用ID）
        if (StringUtils.isNotBlank(config.getSubMchNo())) {
            wxConfig.setSubMchId(config.getSubMchNo());
        }
        if (StringUtils.isNotBlank(params.getSubAppId())) {
            wxConfig.setSubAppId(params.getSubAppId());
        }
        // V3 协议
        wxConfig.setApiV3Key(params.getApiV3Key());
        wxConfig.setCertSerialNo(params.getMerchantSerialNumber());
        wxConfig.setPrivateKeyString(params.getMerchantPrivateKey());
        // V3 平台证书由 WxJava 在首次 V3 调用时自动初始化与轮换，此处不强制联网构建
        WxPayService service = new WxPayServiceImpl();
        service.setConfig(wxConfig);
        return service;
    }
}
