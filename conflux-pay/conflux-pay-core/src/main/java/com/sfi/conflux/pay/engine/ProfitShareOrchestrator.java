package com.sfi.conflux.pay.engine;

import com.sfi.conflux.pay.api.ProfitShareQueryRequest;
import com.sfi.conflux.pay.api.ProfitShareRequest;
import com.sfi.conflux.pay.api.ProfitShareResult;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.ProfitShareHandler;
import com.sfi.conflux.pay.channel.ProfitShareInvocation;
import com.sfi.conflux.pay.channel.ProfitShareSnapshot;
import com.sfi.conflux.pay.model.ProfitShare;
import com.sfi.conflux.pay.model.ProfitShareReceiver;
import com.sfi.conflux.pay.model.ProfitShareState;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 分账编排器：发起分账、分账查单的渠道编排。
 *
 * <p>SDK 无状态：不落库、不推进状态；原支付单的渠道单号
 * （{@code channelOrderNo}）由使用方随请求提供。</p>
 *
 * @author lianghang
 */
@Slf4j
class ProfitShareOrchestrator {

    private final GatewayRuntime rt;

    ProfitShareOrchestrator(GatewayRuntime rt) {
        this.rt = rt;
    }

    /** 发起分账：调用渠道并归一返回受理结果。 */
    ProfitShareResult create(ProfitShareRequest request) {
        request.check();
        String channel = request.getChannelCode();

        long total = 0L;
        for (ProfitShareReceiver receiver : request.getReceivers()) {
            total += receiver.getAmount();
        }

        ProfitShare share = new ProfitShare()
                .setConfigId(request.getConfigId())
                .setBizShareNo(request.getBizShareNo())
                .setBizOrderNo(request.getBizOrderNo())
                .setChannelCode(channel)
                .setChannelPayOrderNo(request.getChannelOrderNo())
                .setAmount(total)
                .setReceivers(request.getReceivers())
                .setState(ProfitShareState.CREATED)
                .setExtParams(request.getChannelExtras() == null ? null : JsonUtils.toJson(request.getChannelExtras()));
        try {
            ProfitShareHandler handler = rt.registry.profitShareHandler(channel);
            ChannelConfig config = rt.configProvider.getConfig(request.getConfigId(), channel);
            ProfitShareInvocation invocation = handler.createProfitShare(share, config);
            if (StringUtils.isNotBlank(invocation.getChannelShareNo())) {
                share.setChannelShareNo(invocation.getChannelShareNo());
            }
            share.setErrorCode(null);
            share.setErrorMsg(null);
            ProfitShareState target = invocation.getState() == null ? ProfitShareState.PROCESSING : invocation.getState();
            share.setState(target);
            if (target == ProfitShareState.FINISHED) {
                share.setFinishTime(rt.now());
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "渠道分账调用失败: " + e.getMessage(), e);
        }
        return ProfitShareResult.of(share);
    }

    /** 分账查单：向渠道查询并归一返回快照。 */
    ProfitShareResult query(ProfitShareQueryRequest request) {
        request.check();
        ProfitShare share = new ProfitShare()
                .setConfigId(request.getConfigId())
                .setBizShareNo(request.getBizShareNo())
                .setChannelCode(request.getChannelCode())
                .setChannelShareNo(request.getChannelShareNo());
        try {
            ProfitShareHandler handler = rt.registry.profitShareHandler(request.getChannelCode());
            ChannelConfig config = rt.configProvider.getConfig(request.getConfigId(), request.getChannelCode());
            ProfitShareSnapshot snapshot = handler.queryProfitShare(share, config);
            if (StringUtils.isNotBlank(snapshot.getChannelShareNo())
                    && StringUtils.isBlank(share.getChannelShareNo())) {
                share.setChannelShareNo(snapshot.getChannelShareNo());
            }
            if (snapshot.getState() != null) {
                share.setState(snapshot.getState());
            }
            if (snapshot.getFinishTime() != null) {
                share.setFinishTime(snapshot.getFinishTime());
            }
        } catch (GatewayException e) {
            log.warn("渠道分账查单失败: bizShareNo={}, error={}", request.getBizShareNo(), e.getMessage());
            throw e;
        }
        return ProfitShareResult.of(share);
    }
}
