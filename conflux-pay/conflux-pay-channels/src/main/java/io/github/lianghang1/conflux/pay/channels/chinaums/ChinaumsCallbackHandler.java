package io.github.lianghang1.conflux.pay.channels.chinaums;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.util.StringUtils;
import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.channel.CallbackConfigLookup;
import io.github.lianghang1.conflux.pay.channel.ChannelAccountKey;
import io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.pay.model.BizOrderType;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天满异步通知处理器：解析 form 报文后「主动查单确认」。
 *
 * <p>天满通知携带 sign，且官方"通知验签规则"给出了基于通讯密钥的验签算法；但本处理器
 * 采用更强的兜底策略：不信任通知内容中的状态与金额，仅用其中的 merOrderId/mid 定位配置，
 * 随后以 OPEN-BODY-SIG 双向认证调用查单接口（/v1/netpay/query）获取权威状态，构造归一化事件。
 * 验签只能证明报文来源，查单拿到的才是渠道当前真实状态，故以查询接口为准；这也是天满官方
 * "以查询接口响应的订单状态更新订单状态"推荐的模式。通讯密钥与 appKey 是否相同未经真实
 * 凭证验证，不在本实现范围内臆造验签。</p>
 *
 * @author lianghang
 */
public class ChinaumsCallbackHandler implements ChannelCallbackHandler {

    private final ChinaumsClient client;

    public ChinaumsCallbackHandler(ChinaumsClient client) {
        this.client = client;
    }

    @Override
    public CallbackEvent parseCallback(ChannelCallbackRequest raw, CallbackConfigLookup configLookup) {
        Map<String, String> form = raw.getFormParams();
        if (form == null || form.isEmpty()) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED, "天满通知报文为空");
        }
        String mid = form.get("mid");
        String merOrderId = form.get("merOrderId");
        if (StringUtils.isBlank(merOrderId)) {
            throw new GatewayException(GatewayErrorCode.CALLBACK_PARSE_FAILED, "天满通知缺少 merOrderId");
        }
        String accountKey = ChannelAccountKey.resolve(raw, mid);
        if (StringUtils.isBlank(accountKey)) {
            throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                    "天满回调需携带 mid 或 accountKey 查询参数以定位渠道配置");
        }
        ChannelConfig config = configLookup.byAccountKey(accountKey);

        // 主动查单：不信任通知内容，以签名查询获取权威状态
        Map<String, Object> business = new LinkedHashMap<String, Object>();
        business.put("merOrderId", merOrderId);
        JsonNode root = client.execute("回调查单", ChinaumsClient.API_QUERY, config, business);
        String status = ChinaumsClient.text(root, "status");
        PaymentState state = ChinaumsStateTranslator.toPaymentState(status);

        CallbackEvent event = new CallbackEvent()
                .setType(BizOrderType.PAYMENT)
                .setAppId(config.getAppId())
                .setBizOrderNo(merOrderId)
                .setChannelOrderNo(ChinaumsClient.text(root, "targetOrderId"))
                .setSuccess(state == PaymentState.SUCCEEDED)
                .setAmount(ChinaumsClient.longValue(root, "totalAmount"))
                .setOccurTime(ChinaumsStateTranslator.toLocalDateTime(ChinaumsClient.text(root, "payTime")));
        event.setRaw(new LinkedHashMap<String, String>(form));
        return event;
    }

    @Override
    public String successReply() {
        // 天满要求处理成功应答 "SUCCESS"
        return "SUCCESS";
    }

    @Override
    public String failureReply() {
        // 天满要求处理失败应答 "FAILED"（支付结果通知规则）
        return "FAILED";
    }
}
