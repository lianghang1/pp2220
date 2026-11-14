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
import java.util.Map;

/**
 * 微信分账处理器：V3（profitSharingV3）与 V2（profitSharing）双路径，
 * 按 wxApiVersion 路由；关联支付单的渠道单号（transactionId）为必需入参。
 *
 * <p>接收方类型映射：MERCHANT → MERCHANT_ID，PERSONAL → PERSONAL_OPENID；
 * V2 分账接收方以 JSON 串承载（WxJava 契约），发起前提为订单分账已完成解冻
 * 或 unfreezeUnsplit 置 true。终态确认：V3 分账结果查询（okhttp 直连 +
 * 商户私钥签名 Authorization）或分账结果回调。</p>
 *
 * @author lianghang
 */
@Slf4j
public class WechatProfitShareHandler implements ProfitShareHandler {

    private final WechatClientHolder holder;
    private final WechatAuthorizationBuilder authBuilder = new WechatAuthorizationBuilder();
    private final okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build();


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
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        String transactionId = share.getChannelPayOrderNo();
        if (StringUtils.isBlank(transactionId)) {
            throw new GatewayException(GatewayErrorCode.STATE_ILLEGAL,
                    "分账查询缺少渠道支付单号（channelPayOrderNo）: shareId={}", share.getShareId());
        }
        String urlPath = "/v3/profitsharing/orders/" + share.getShareId()
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

    /** 分账结果查询（WxJava 未封装该接口）：okhttp 直连 + 商户私钥签名 Authorization 头。 */
    private Map<String, Object> directQuery(ChannelConfig config, WechatParams params, String urlPath) {
        try {
            String authorization = authBuilder.buildAuthorization(params, "GET", urlPath, "");
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(WechatApiBase.PAY_BASE_URL + urlPath)
                    .header("Authorization", authorization)
                    .header("Accept", "application/json")
                    .get()
                    .build();
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                            "微信分账查询失败: status={}, body={}", response.code(), body);
                }
                return io.github.lianghang1.paygateway.util.JsonUtils.toMap(body);
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信分账查询异常: {}", e.getMessage(), e);
        }
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
