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
                holder.execute("分账", config, client -> client.execute(request));
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
                holder.execute("分账查单", config, client -> client.execute(request));
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
