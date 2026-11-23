package com.sfi.conflux.invoice.engine;

import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.invoice.channel.ChannelInstance;
import com.sfi.conflux.invoice.exception.InvoiceErrorCode;
import com.sfi.conflux.invoice.model.InvoiceType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 渠道实例路由：按企业（销方税号）与票种做通道分级择优。
 *
 * <p>优先级 = 通道级别（{@code ChannelTier.getPriority()}）× 1000 + 实例权重。
 * 乐企/税局直连优先于服务商开放 API，服务商 API 优先于电局网页/RPA 兜底；
 * 显式指定 {@code channelCode} 时只在该渠道内选择（灰度、降级用）。</p>
 *
 * @author lianghang
 */
@Slf4j
public class ChannelInstanceRouter {

    private final InvoiceRuntime rt;

    public ChannelInstanceRouter(InvoiceRuntime rt) {
        this.rt = rt;
    }

    /**
     * @param preferredChannelCode 指定渠道编码（可空）
     * @return 择优后的可用实例
     */
    public ChannelInstance route(String configId, String sellerTaxNo, InvoiceType type,
                                 String preferredChannelCode) {
        if (preferredChannelCode != null && preferredChannelCode.length() > 0) {
            // 显式指定但渠道未注册时给出明确错误，不要退化成「无可用实例」
            rt.registry.provider(preferredChannelCode);
            rt.registry.assertTypeSupported(preferredChannelCode, type);
        }

        List<ChannelInstance> candidates = new ArrayList<ChannelInstance>();
        List<ChannelInstance> all = rt.configProvider.instances(configId, sellerTaxNo);
        if (all != null) {
            for (ChannelInstance instance : all) {
                if (instance == null || !instance.isEnabled()) {
                    continue;
                }
                if (preferredChannelCode != null && preferredChannelCode.length() > 0
                        && !preferredChannelCode.equals(instance.getChannelCode())) {
                    continue;
                }
                String code = instance.getChannelCode();
                if (!rt.registry.registeredChannels().contains(code)) {
                    log.warn("发票渠道实例[{}]对应的渠道[{}]未注册，路由时跳过",
                            instance.getInstanceId(), code);
                    continue;
                }
                if (!supportsType(rt, code, type)) {
                    continue;
                }
                candidates.add(instance);
            }
        }
        if (candidates.isEmpty()) {
            throw new GatewayException(InvoiceErrorCode.CHANNEL_INSTANCE_NOT_FOUND,
                    "未找到可用发票渠道实例: configId={}, sellerTaxNo={}, type={}, preferredChannel={}",
                    configId, sellerTaxNo, type, preferredChannelCode);
        }
        Collections.sort(candidates, new Comparator<ChannelInstance>() {
            @Override
            public int compare(ChannelInstance a, ChannelInstance b) {
                return Integer.compare(b.routingScore(), a.routingScore());
            }
        });
        ChannelInstance chosen = candidates.get(0);
        log.debug("发票渠道路由: configId={}, taxNo={}, type={}, channel={}, instance={}, score={}",
                configId, sellerTaxNo, type, chosen.getChannelCode(),
                chosen.getInstanceId(), chosen.routingScore());
        return chosen;
    }

    /** 独立的票种支持判定，避免把「不支持」当异常抛出而中断候选遍历。 */
    private static boolean supportsType(InvoiceRuntime rt, String code, InvoiceType type) {
        try {
            rt.registry.assertTypeSupported(code, type);
            return true;
        } catch (GatewayException e) {
            return false;
        }
    }
}
