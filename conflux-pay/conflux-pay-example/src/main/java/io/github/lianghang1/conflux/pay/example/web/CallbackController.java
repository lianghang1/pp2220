package io.github.lianghang1.conflux.pay.example.web;

import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.engine.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 渠道回调演示入口：SDK 验签解析出归一事件后，使用方按事件中的业务单号
 * 定位自己的订单，判重、金额校验与状态推进自行完成，再以渠道认可的应答写回。
 * 异常时以失败应答触发渠道重推——重复回调的幂等由使用方保证。
 *
 * @author lianghang
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CallbackController {

    private final PaymentGateway paymentGateway;

    @PostMapping("/callback/{channelCode}")
    public ResponseEntity<String> onChannelCallback(
            @PathVariable String channelCode,
            @RequestHeader Map<String, String> headers,
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {

        ChannelCallbackRequest callbackRequest = new ChannelCallbackRequest()
                .setChannelCode(channelCode)
                .setHeaders(new HashMap<String, String>(headers))
                .setQueryParams(queryParams == null ? new HashMap<String, String>() : queryParams)
                .setFormParams(collectFormParams(request))
                .setBody(body);
        try {
            // 实际业务应查自己 DB（用回调里的业务单号）定位 configId，这里演示从查询参数取
            String configId = queryParams == null ? null : queryParams.get("configId");
            CallbackEvent event = paymentGateway.parseCallback(configId, callbackRequest);
            handleEvent(event);
            return ResponseEntity.status(200).body(paymentGateway.callbackSuccessReply(channelCode));
        } catch (GatewayException e) {
            log.warn("回调处理失败: channel={}, error={}", channelCode, e.getMessage());
            return ResponseEntity.status(200).body(paymentGateway.callbackFailureReply(channelCode));
        }
    }

    /**
     * 业务联动演示位：按 {@code event.getBizOrderNo()} 定位自己的订单，
     * 判重（同一事件可能重复到达）、校验金额后推进自己的订单状态，
     * 并联动订单系统、库存系统等下游业务。
     */
    private void handleEvent(CallbackEvent event) {
        log.info("收到渠道回调: type={}, bizOrderNo={}, channelOrderNo={}, success={}, amount={}",
                event.getType(), event.getBizOrderNo(), event.getChannelOrderNo(),
                event.isSuccess(), event.getAmount());
    }

    /** 提取表单参数（application/x-www-form-urlencoded 场景）。 */
    private Map<String, String> collectFormParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<String, String>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }
}
