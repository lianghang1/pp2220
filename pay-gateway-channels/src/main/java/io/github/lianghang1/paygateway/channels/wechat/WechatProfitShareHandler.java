package io.github.lianghang1.paygateway.channels.wechat;

import com.github.binarywang.wxpay.bean.profitsharing.request.ProfitSharingRequest;
import com.github.binarywang.wxpay.bean.profitsharing.request.ProfitSharingV3Request;
import com.github.binarywang.wxpay.bean.profitsharing.result.ProfitSharingResult;
import com.github.binarywang.wxpay.bean.profitsharing.result.ProfitSharingV3Result;
import com.github.binarywang.wxpay.service.WxPayService;
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
 * 微信分账处理器：V3（profitSharingV3）与 V2（profitSharing）双路径，
 * 按 wxApiVersion 路由；关联支付单的渠道单号（transactionId）为必需入参。
 *
 * <p>接收方类型映射：MERCHANT → MERCHANT_ID，PERSONAL → PERSONAL_OPENID；
 * V2 分账接收方以 JSON 串承载（WxJava 契约），发起前提为订单分账已完成解冻
 * 或 unfreezeUnsplit 置 true。</p>
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
        WxPayService service = holder.service(config);
        String transactionId = share.getChannelPayOrderNo();
        if (StringUtils.isBlank(transactionId)) {
            throw new GatewayException(GatewayErrorCode.STATE_ILLEGAL,
                    "分账缺少渠道支付单号（channelPayOrderNo）: shareId={}", share.getShareId());
        }
        if (params.isV2()) {
            ProfitSharingRequest request = new ProfitSharingRequest();
            request.setTransactionId(transactionId);
            request.setOutOrderNo(share.getShareId());
            request.setReceivers(buildV2ReceiversJson(share.getReceivers()));
            ProfitSharingResult result = wrap("V2 分账", () ->
                    service.getProfitSharingService().profitSharing(request));
            return new ProfitShareInvocation()
                    .setChannelShareNo(result.getOrderId())
                    .setState(ProfitShareState.PROCESSING);
        }
        ProfitSharingV3Request request = new ProfitSharingV3Request();
        request.setAppid(params.getAppId());
        if (StringUtils.isNotBlank(config.getSubMchNo())) {
            request.setSubMchId(config.getSubMchNo());
        }
        request.setTransactionId(transactionId);
        request.setOutOrderNo(share.getShareId());
        request.setUnfreezeUnsplit(true);
        List<ProfitSharingV3Request.Receiver> receivers = new ArrayList<ProfitSharingV3Request.Receiver>();
        for (ProfitShareReceiver receiver : share.getReceivers()) {
            ProfitSharingV3Request.Receiver item = new ProfitSharingV3Request.Receiver();
            item.setType("MERCHANT".equalsIgnoreCase(receiver.getReceiverType())
                    ? "MERCHANT_ID" : "PERSONAL_OPENID");
            item.setAccount(receiver.getReceiverAccount());
            item.setAmount(receiver.getAmount().intValue());
            item.setDescription(StringUtils.defaultIfBlank(receiver.getDescription(), "分账"));
            receivers.add(item);
        }
        request.setReceivers(receivers);
        ProfitSharingV3Result result = wrap("V3 分账", () ->
                service.getProfitSharingService().profitSharingV3(request));
        return new ProfitShareInvocation()
                .setChannelShareNo(result.getOrderId())
                .setState(ProfitShareState.PROCESSING);
    }

    @Override
    public ProfitShareSnapshot queryProfitShare(ProfitShare share, ChannelConfig config) {
        throw new GatewayException(GatewayErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED,
                "微信分账结果以回调/微信商户平台为准，暂不提供独立查询");
    }

    /** V2 分账接收方 JSON 串（WxJava 契约：receivers 直接承载 JSON 文本）。 */
    private String buildV2ReceiversJson(List<ProfitShareReceiver> receivers) {
        StringBuilder json = new StringBuilder("[");
        for (ProfitShareReceiver receiver : receivers) {
            json.append("{\"type\":\"").append("MERCHANT".equalsIgnoreCase(receiver.getReceiverType())
                            ? "MERCHANT_ID" : "PERSONAL_OPENID")
                    .append("\",\"account\":\"").append(receiver.getReceiverAccount())
                    .append("\",\"amount\":").append(receiver.getAmount())
                    .append(",\"description\":\"").append(StringUtils.defaultIfBlank(
                            receiver.getDescription(), "分账"))
                    .append("\"},");
        }
        if (json.length() > 1) {
            json.setLength(json.length() - 1);
        }
        return json.append(']').toString();
    }

    private <T> T wrap(String action, WechatCall<T> call) {
        try {
            return call.call();
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信{}失败: {}", action, e.getMessage(), e);
        }
    }
}
