package com.sfi.conflux.pay.channels.alipay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.sfi.conflux.common.exception.GatewayException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 回归测试：execute 与 executeRaw 的业务 subCode 处理差异。
 *
 * <p>查单/退款查单需要读取 subCode 归一为业务状态（交易不存在=支付中、退款查无记录=处理中），
 * 必须走 executeRaw 拿到原始响应；若误走 execute，业务失败会先被抛成异常，导致特判分支不可达。</p>
 *
 * @author lianghang
 */
class AlipayExecuteRawTest {

    private final AlipayClientHolder holder = new AlipayClientHolder();

    private static AlipayParams params() {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("appId", "2021000000000001");
        properties.put("privateKey", "-----BEGIN RSA PRIVATE KEY-----\nMIIB\n-----END RSA PRIVATE KEY-----");
        properties.put("alipayPublicKey", "MIIBIjANBgkq");
        return AlipayParams.fromMap(properties);
    }

    private static AlipayTradeQueryResponse tradeNotExist() {
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.TRADE_NOT_EXIST");
        response.setSubMsg("交易不存在");
        return response;
    }

    @Test
    void executeShouldThrowOnBusinessFailure() {
        // execute 把 subCode 业务失败统一抛成渠道异常
        assertThatThrownBy(() -> holder.execute("查单", params(), (client, authToken) -> tradeNotExist()))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void executeRawShouldKeepBusinessFailureResponse() {
        // executeRaw 保留原始响应，让上层特判分支可执行
        AlipayTradeQueryResponse response = holder.executeRaw("查单", params(), (client, authToken) -> tradeNotExist());
        assertThat(response).isNotNull();
        assertThat(response.getSubCode()).isEqualTo("ACQ.TRADE_NOT_EXIST");
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void executeRawShouldStillWrapSdkException() {
        // executeRaw 仍需收敛 SDK 异常，不能把受检异常漏给上层
        assertThatThrownBy(() -> holder.executeRaw("查单", params(), (client, authToken) -> {
            throw new AlipayApiException("sdk boom");
        })).isInstanceOf(GatewayException.class);
    }
}
