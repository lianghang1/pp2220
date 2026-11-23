package com.sfi.conflux.pay.channels.wechat;

import com.github.binarywang.wxpay.bean.profitsharing.request.ProfitSharingRequest;
import com.github.binarywang.wxpay.bean.profitsharing.request.ProfitSharingV3Request;
import com.github.binarywang.wxpay.bean.profitsharing.result.ProfitSharingResult;
import com.github.binarywang.wxpay.bean.profitsharing.result.ProfitSharingV3Result;
import com.github.binarywang.wxpay.service.WxPayService;
import com.sfi.conflux.common.http.HttpResponse;
import com.sfi.conflux.common.http.HttpTransport;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.ProfitShareHandler;
import com.sfi.conflux.pay.channel.ProfitShareInvocation;
import com.sfi.conflux.pay.channel.ProfitShareSnapshot;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.ProfitShare;
import com.sfi.conflux.pay.model.ProfitShareReceiver;
import com.sfi.conflux.pay.model.ProfitShareState;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信分账处理器：V3（profitSharingV3）与 V2（profitSharing）双路径，
 * 按 wxApiVersion 路由；关联支付单的渠道单号（transactionId）为必需入参。
 *
 * <p>接收方类型映射：MERCHANT → MERCHANT_ID，PERSONAL → PERSONAL_OPENID；
 * V2 分账接收方以 JSON 串承载（WxJava 契约），发起前提为订单分账已完成解冻
 * 或 unfreezeUnsplit 置 true。终态确认：V3 分账结果查询（common HTTP 传输层 +
 * 商户私钥签名 Authorization）或分账结果回调。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatProfitShareHandler implements ProfitShareHandler {

    private final WechatClientHolder holder;
    private final WechatAuthorizationBuilder authBuilder = new WechatAuthorizationBuilder();

    public WechatProfitShareHandler(WechatClientHolder holder) {
        this.holder = holder;
    }

    @Override
    public void bindReceiver(ProfitShareReceiver receiver, ChannelConfig config) {
        WechatDivisionReceiverSupport.bindReceiver(receiver, config, holder);
    }

    @Override
    public void unbindReceiver(ProfitShareReceiver receiver, ChannelConfig config) {
        WechatDivisionReceiverSupport.unbindReceiver(receiver, config, holder);
    }

    @Override
    public ProfitShareInvocation createProfitShare(ProfitShare share, ChannelConfig config) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        String transactionId = share.getChannelPayOrderNo();
        if (StringUtils.isBlank(transactionId)) {
            throw new GatewayException(GatewayErrorCode.STATE_ILLEGAL,
                    "分账缺少渠道支付单号（channelPayOrderNo）: shareId={}", share.getBizShareNo());
        }
        if (params.isV2()) {
            ProfitSharingRequest request = new ProfitSharingRequest();
            request.setTransactionId(transactionId);
            request.setOutOrderNo(share.getBizShareNo());
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
        request.setOutOrderNo(share.getBizShareNo());
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
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        String transactionId = share.getChannelPayOrderNo();
        if (StringUtils.isBlank(transactionId)) {
            throw new GatewayException(GatewayErrorCode.STATE_ILLEGAL,
                    "分账查询缺少渠道支付单号（channelPayOrderNo）: shareId={}", share.getBizShareNo());
        }
        String urlPath = "/v3/profitsharing/orders/" + share.getBizShareNo()
                + "?transaction_id=" + transactionId;
        Map<String, Object> result = directQuery(config, params, urlPath);
        String state = result.get("state") == null ? null : String.valueOf(result.get("state"));
        ProfitShareState profitShareState = "FINISHED".equals(state)
                ? ProfitShareState.FINISHED : ProfitShareState.PROCESSING;
        return new ProfitShareSnapshot()
                .setChannelShareNo(result.get("order_id") == null
                        ? share.getChannelShareNo() : String.valueOf(result.get("order_id")))
                .setState(profitShareState)
                .setFinishTime(WechatStateTranslator.toLocalDateTime(
                        result.get("finish_time") == null ? null : String.valueOf(result.get("finish_time"))))
                .setRawMsg(state);
    }

    /** 分账结果查询（WxJava 未封装该接口）：common HTTP 传输层 + 商户私钥签名 Authorization 头。 */
    private Map<String, Object> directQuery(ChannelConfig config, WechatParams params, String urlPath) {
        try {
            String authorization = authBuilder.buildAuthorization(params, "GET", urlPath, "");
            Map<String, String> headers = new java.util.LinkedHashMap<String, String>();
            headers.put("Authorization", authorization);
            headers.put("Accept", "application/json");
            HttpResponse response = HttpTransport.get(WechatApiBase.PAY_BASE_URL + urlPath, headers,
                    10000L, 20000L, 10000L);
            String body = response.getBody();
            if (!response.isSuccessful()) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "微信分账查询失败: status={}, body={}", response.getStatusCode(), body);
            }
            return com.sfi.conflux.common.util.JsonUtils.toMap(body);
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信分账查询异常: {}", e.getMessage(), e);
        }
    }

    /** V2 分账接收方 JSON 串（WxJava 契约：receivers 直接承载 JSON 文本）。包可见便于测试。 */
    static String buildV2ReceiversJson(List<ProfitShareReceiver> receivers) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (ProfitShareReceiver receiver : receivers) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("type", "MERCHANT".equalsIgnoreCase(receiver.getReceiverType())
                    ? "MERCHANT_ID" : "PERSONAL_OPENID");
            item.put("account", receiver.getReceiverAccount());
            item.put("amount", receiver.getAmount());
            item.put("description", StringUtils.defaultIfBlank(receiver.getDescription(), "分账"));
            items.add(item);
        }
        return JsonUtils.toJson(items);
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
