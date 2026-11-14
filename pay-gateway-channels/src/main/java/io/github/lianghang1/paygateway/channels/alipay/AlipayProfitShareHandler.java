package io.github.lianghang1.paygateway.channels.alipay;

import com.alipay.api.domain.AlipayTradeOrderSettleModel;
import com.alipay.api.domain.AlipayTradeOrderSettleQueryModel;
import com.alipay.api.domain.OpenApiRoyaltyDetailInfoPojo;
import com.alipay.api.domain.RoyaltyDetail;
import com.alipay.api.request.AlipayTradeOrderSettleQueryRequest;
import com.alipay.api.request.AlipayTradeOrderSettleRequest;
import com.alipay.api.response.AlipayTradeOrderSettleQueryResponse;
import com.alipay.api.response.AlipayTradeOrderSettleResponse;
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
 * 支付宝分账处理器：结算规则接口（alipay.trade.order.settle），
 * outRequestNo = 网关分账单号，tradeNo = 关联支付单的渠道单号。
 *
 * <p>接收方明细金额为元字符串；受理同步返回，单接收方失败经查询接口按明细状态判定。</p>
 *
 * @author lianghang
 */
@Slf4j
public class AlipayProfitShareHandler implements ProfitShareHandler {

    private final AlipayClientHolder holder;

    public AlipayProfitShareHandler(AlipayClientHolder holder) {
        this.holder = holder;
    }

    /** 绑定分账接收方（支付宝结算规则关系绑定 alipay.trade.royalty.relation.bind）。 */
    @Override
    public void bindReceiver(ProfitShareReceiver receiver, ChannelConfig config) {
        applyRelation(receiver, config, new com.alipay.api.request.AlipayTradeRoyaltyRelationBindRequest(),
                new com.alipay.api.domain.AlipayTradeRoyaltyRelationBindModel(), "绑定");
    }

    /** 解绑分账接收方（alipay.trade.royalty.relation.unbind）。 */
    @Override
    public void unbindReceiver(ProfitShareReceiver receiver, ChannelConfig config) {
        applyRelation(receiver, config, new com.alipay.api.request.AlipayTradeRoyaltyRelationUnbindRequest(),
                new com.alipay.api.domain.AlipayTradeRoyaltyRelationUnbindModel(), "解绑");
    }

    /** 接收方关系绑定/解绑公共实现（outRequestNo 随机生成、单接收方明细）。 */
    private void applyRelation(ProfitShareReceiver receiver, ChannelConfig config,
                               com.alipay.api.AlipayRequest<?> request,
                               Object model,
                               String action) {
        com.alipay.api.domain.RoyaltyEntity entity = new com.alipay.api.domain.RoyaltyEntity();
        entity.setAccount(receiver.getReceiverAccount());
        entity.setMemo(StringUtils.defaultIfBlank(receiver.getDescription(), "分账"));
        java.util.List<com.alipay.api.domain.RoyaltyEntity> entities =
                java.util.Collections.singletonList(entity);
        String outRequestNo = io.github.lianghang1.paygateway.util.IdGenerator.nextProfitShareId() + action;
        if (request instanceof com.alipay.api.request.AlipayTradeRoyaltyRelationBindRequest) {
            com.alipay.api.domain.AlipayTradeRoyaltyRelationBindModel bindModel =
                    (com.alipay.api.domain.AlipayTradeRoyaltyRelationBindModel) model;
            bindModel.setOutRequestNo(outRequestNo);
            bindModel.setReceiverList(entities);
            ((com.alipay.api.request.AlipayTradeRoyaltyRelationBindRequest) request).setBizModel(bindModel);
        } else {
            com.alipay.api.domain.AlipayTradeRoyaltyRelationUnbindModel unbindModel =
                    (com.alipay.api.domain.AlipayTradeRoyaltyRelationUnbindModel) model;
            unbindModel.setOutRequestNo(outRequestNo);
            unbindModel.setReceiverList(entities);
            ((com.alipay.api.request.AlipayTradeRoyaltyRelationUnbindRequest) request).setBizModel(unbindModel);
        }
        holder.execute("分账接收方" + action, config, (client, authToken) -> client.execute(request, authToken));
        log.info("支付宝分账接收方{}完成: account={}", action,
                io.github.lianghang1.paygateway.util.MaskUtils.custom(receiver.getReceiverAccount(), 3, 2));
    }

    @Override
    public ProfitShareInvocation createProfitShare(ProfitShare share, ChannelConfig config) {
        AlipayTradeOrderSettleRequest request = new AlipayTradeOrderSettleRequest();
        AlipayTradeOrderSettleModel model = new AlipayTradeOrderSettleModel();
        model.setOutRequestNo(share.getShareId());
        if (StringUtils.isBlank(share.getChannelPayOrderNo())) {
            throw new GatewayException(GatewayErrorCode.STATE_ILLEGAL,
                    "分账缺少渠道支付单号（channelPayOrderNo）: shareId={}", share.getShareId());
        }
        model.setTradeNo(share.getChannelPayOrderNo());
        List<OpenApiRoyaltyDetailInfoPojo> royalties = new ArrayList<OpenApiRoyaltyDetailInfoPojo>();
        for (ProfitShareReceiver receiver : share.getReceivers()) {
            OpenApiRoyaltyDetailInfoPojo item = new OpenApiRoyaltyDetailInfoPojo();
            item.setTransIn(receiver.getReceiverAccount());
            item.setAmount(AlipayStateTranslator.toYuan(receiver.getAmount()));
            item.setDesc(receiver.getDescription() == null ? "分账" : receiver.getDescription());
            royalties.add(item);
        }
        model.setRoyaltyParameters(royalties);
        request.setBizModel(model);
        AlipayTradeOrderSettleResponse response =
                holder.execute("分账", config, (client, authToken) -> client.execute(request, authToken));
        return new ProfitShareInvocation()
                .setChannelShareNo(response.getSettleNo())
                .setState(ProfitShareState.FINISHED);
    }

    @Override
    public ProfitShareSnapshot queryProfitShare(ProfitShare share, ChannelConfig config) {
        AlipayTradeOrderSettleQueryRequest request = new AlipayTradeOrderSettleQueryRequest();
        AlipayTradeOrderSettleQueryModel model = new AlipayTradeOrderSettleQueryModel();
        model.setOutRequestNo(share.getShareId());
        request.setBizModel(model);
        AlipayTradeOrderSettleQueryResponse response =
                holder.execute("分账查单", config, (client, authToken) -> client.execute(request, authToken));
        ProfitShareState state = ProfitShareState.FINISHED;
        List<RoyaltyDetail> details = response.getRoyaltyDetailList();
        if (details != null) {
            for (RoyaltyDetail detail : details) {
                if (detail.getState() != null && !"SUCCESS".equals(detail.getState())) {
                    state = ProfitShareState.PROCESSING;
                }
            }
        }
        return new ProfitShareSnapshot()
                .setChannelShareNo(response.getOutRequestNo())
                .setState(state)
                .setRawMsg(details == null ? null : details.size() + " details");
    }
}
