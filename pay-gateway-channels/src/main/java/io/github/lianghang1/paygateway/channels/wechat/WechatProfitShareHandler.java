package io.github.lianghang1.paygateway.channels.wechat;

import com.wechat.pay.java.service.profitsharing.model.CreateOrderReceiver;
import com.wechat.pay.java.service.profitsharing.model.CreateOrderRequest;
import com.wechat.pay.java.service.profitsharing.model.OrderStatus;
import com.wechat.pay.java.service.profitsharing.model.OrdersEntity;
import com.wechat.pay.java.service.profitsharing.model.QueryOrderRequest;
import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.channel.ProfitShareHandler;
import io.github.lianghang1.paygateway.channel.ProfitShareInvocation;
import io.github.lianghang1.paygateway.channel.ProfitShareSnapshot;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.model.ProfitShare;
import io.github.lianghang1.paygateway.model.ProfitShareReceiver;
import io.github.lianghang1.paygateway.model.ProfitShareState;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信分账处理器：一次请求分账（outOrderNo = 网关分账单号），
 * 接收方类型按约定映射（MERCHANT → MERCHANT_ID，PERSONAL → PERSONAL_OPENID）。
 *
 * <p>接收方需先在微信侧完成分账接收方添加（本包提供 addReceiver 入口外的
 * 声明式扩展由使用方按需调用 SDK 完成）；分账发起前提是订单已解冻或
 * unfreezeUnsplit 置为 true（本实现固定 true）。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatProfitShareHandler implements ProfitShareHandler {

    private final WechatClientHolder holder;

    public WechatProfitShareHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public ProfitShareInvocation createProfitShare(ProfitShare share, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAppid(params.getAppId());
        if (config.getSubMchNo() != null) {
            request.setSubMchid(config.getSubMchNo());
        }
        request.setTransactionId(share.getChannelPayOrderNo());
        if (StringUtils.isBlank(request.getTransactionId())) {
            throw new GatewayException(GatewayErrorCode.STATE_ILLEGAL,
                    "分账缺少渠道支付单号（channelPayOrderNo）: shareId={}", share.getShareId());
        }
        request.setOutOrderNo(share.getShareId());
        request.setUnfreezeUnsplit(true);
        List<CreateOrderReceiver> receivers = new ArrayList<CreateOrderReceiver>();
        for (ProfitShareReceiver receiver : share.getReceivers()) {
            CreateOrderReceiver item = new CreateOrderReceiver();
            item.setType("MERCHANT".equalsIgnoreCase(receiver.getReceiverType())
                    ? "MERCHANT_ID" : "PERSONAL_OPENID");
            item.setAccount(receiver.getReceiverAccount());
            item.setAmount(receiver.getAmount());
            item.setDescription(receiver.getDescription() == null ? "分账" : receiver.getDescription());
            receivers.add(item);
        }
        request.setReceivers(receivers);

        OrdersEntity entity = wrap("分账", () -> holder.clients(config).profitSharingService().createOrder(request));
        ProfitShareState state = entity.getState() == null ? ProfitShareState.PROCESSING
                : WechatStateTranslator.toProfitShareState(entity.getState().name());
        return new ProfitShareInvocation()
                .setChannelShareNo(entity.getOrderId())
                .setState(state);
    }

    @Override
    public ProfitShareSnapshot queryProfitShare(ProfitShare share, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        QueryOrderRequest request = new QueryOrderRequest();
        request.setSubMchid(config.getSubMchNo());
        request.setTransactionId(share.getChannelPayOrderNo());
        request.setOutOrderNo(share.getShareId());
        OrdersEntity entity = wrap("分账查单", () -> holder.clients(config).profitSharingService().queryOrder(request));
        OrderStatus status = entity.getState();
        return new ProfitShareSnapshot()
                .setChannelShareNo(entity.getOrderId())
                .setState(WechatStateTranslator.toProfitShareState(status == null ? null : status.name()))
                .setRawMsg(status == null ? null : status.name());
    }

    private <T> T wrap(String action, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }
}
