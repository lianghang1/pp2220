package com.sfi.conflux.pay.channels.wechat;

import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.pay.model.ProfitShareReceiver;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 微信 V2 分账接收方 JSON 序列化回归：账号/描述含引号、反斜杠等特殊字符时必须
 * 转义为合法 JSON（历史手工拼接会产生非法报文）。
 */
class WechatProfitShareHandlerTest {

    @Test
    void v2ReceiversJsonShouldEscapeSpecialCharacters() {
        ProfitShareReceiver receiver = new ProfitShareReceiver()
                .setReceiverType("MERCHANT")
                .setReceiverAccount("acc\"with\"quote")
                .setAmount(100L)
                .setDescription("分账\\备注\"含引号\"");

        String json = WechatProfitShareHandler.buildV2ReceiversJson(Arrays.asList(receiver));

        // 必须是合法 JSON，且反序列化后字段值原样保留（证明做了转义而非拼接）
        JsonNode item = JsonUtils.readTree(json).get(0);
        assertThat(item.get("type").asText()).isEqualTo("MERCHANT_ID");
        assertThat(item.get("account").asText()).isEqualTo("acc\"with\"quote");
        assertThat(item.get("description").asText()).isEqualTo("分账\\备注\"含引号\"");
        assertThat(item.get("amount").asLong()).isEqualTo(100L);
    }

    @Test
    void v2ReceiversJsonShouldDefaultDescriptionAndMapPersonalType() {
        ProfitShareReceiver receiver = new ProfitShareReceiver()
                .setReceiverType("PERSONAL")
                .setReceiverAccount("openid-123")
                .setAmount(50L);
        String json = WechatProfitShareHandler.buildV2ReceiversJson(Arrays.asList(receiver));
        JsonNode item = JsonUtils.readTree(json).get(0);
        assertThat(item.get("type").asText()).isEqualTo("PERSONAL_OPENID");
        assertThat(item.get("description").asText()).isEqualTo("分账");
    }
}
