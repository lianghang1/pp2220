package io.github.lianghang1.paygateway.example.web;

import io.github.lianghang1.paygateway.api.ChannelCallbackRequest;
import io.github.lianghang1.paygateway.api.ChannelCallbackReply;
import io.github.lianghang1.paygateway.engine.PaymentGateway;
import io.github.lianghang1.paygateway.exception.GatewayException;
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
 * 渠道回调演示入口：使用方自行暴露回调地址，把原始请求交给网关处理后
 * 以渠道认可的应答写回。异常时以失败应答触发渠道重推。
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
            ChannelCallbackReply reply = paymentGateway.handleCallback(callbackRequest);
            return ResponseEntity.status(reply.getStatusCode()).body(reply.getBody());
        } catch (GatewayException e) {
            log.warn("回调处理失败: channel={}, error={}", channelCode, e.getMessage());
            return ResponseEntity.status(200).body(paymentGateway.callbackFailureReply(channelCode));
        }
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
