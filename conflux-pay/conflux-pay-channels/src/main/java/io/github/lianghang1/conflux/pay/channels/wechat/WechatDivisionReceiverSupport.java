package io.github.lianghang1.conflux.pay.channels.wechat;

import com.github.binarywang.wxpay.bean.profitsharing.request.ProfitSharingReceiverRequest;
import com.github.binarywang.wxpay.service.WxPayService;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.model.ProfitShareReceiver;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;

/**
 * 微信分账接收方绑定/解绑（WxJava ProfitSharingService.addReceiver /
 * removeReceiver，receiver 为 JSON 串契约）。绑定/解绑仅在服务商模式下透传
 * WxJava；普通商户分账接收方在商户平台配置。
 *
 * @author lianghang
 */
class WechatDivisionReceiverSupport {

    private WechatDivisionReceiverSupport() {
    }

    /** 绑定分账接收方。 */
    static void bindReceiver(ProfitShareReceiver receiver, ChannelConfig config,
                             WechatClientHolder holder) {
        apply(receiver, config, holder, "/v2/profitsharing/add-receiver", "绑定");
    }

    /** 解绑分账接收方。 */
    static void unbindReceiver(ProfitShareReceiver receiver, ChannelConfig config,
                               WechatClientHolder holder) {
        apply(receiver, config, holder, "/v2/profitsharing/remove-receiver", "解绑");
    }

    private static void apply(ProfitShareReceiver receiver, ChannelConfig config,
                              WechatClientHolder holder, String apiPath, String action) {
        WechatParams params = WechatClientHolder.paramsOf(config);
        WxPayService service = holder.service(config);
        ProfitSharingReceiverRequest request = new ProfitSharingReceiverRequest();
        request.setReceiver(receiverJson(receiver, params));
        try {
            if (apiPath.endsWith("add-receiver")) {
                service.getProfitSharingService().addReceiver(request);
            } else {
                service.getProfitSharingService().removeReceiver(request);
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new io.github.lianghang1.conflux.common.exception.GatewayException(
                    io.github.lianghang1.conflux.pay.exception.GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "微信分账接收方{}失败: {}", action, e.getMessage(), e);
        }
    }

    /** 接收方 JSON 串（V2 契约：type/account/name/relation_type/description）。 */
    private static String receiverJson(ProfitShareReceiver receiver, WechatParams params) {
        String type = "MERCHANT".equalsIgnoreCase(receiver.getReceiverType())
                ? "MERCHANT_ID" : "PERSONAL_OPENID";
        StringBuilder json = new StringBuilder("{\"type\":\"").append(type)
                .append("\",\"account\":\"").append(StringUtils.defaultString(receiver.getReceiverAccount()))
                .append("\"");
        if (StringUtils.isNotBlank(receiver.getRelationType())) {
            json.append(",\"relation_type\":\"").append(receiver.getRelationType()).append("\"");
        }
        json.append(",\"description\":\"").append(StringUtils.defaultIfBlank(
                receiver.getDescription(), "分账")).append("\"}");
        return json.toString();
    }
}
