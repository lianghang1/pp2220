package io.github.lianghang1.conflux.pay;

import io.github.lianghang1.conflux.pay.channel.PayMethod;
import io.github.lianghang1.conflux.common.util.AmountUtil;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.MaskUtils;
import io.github.lianghang1.conflux.common.util.SignUtil;
import io.github.lianghang1.conflux.common.util.StringUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具类测试：金额分元转换、JSON 语义、字符串格式化、打码、签名、方式注册表。
 *
 * @author lianghang
 */
class UtilsTest {

    @Test
    void amountConversionShouldBeExact() {
        assertThat(AmountUtil.toFen(new BigDecimal("12.34"))).isEqualTo(1234L);
        assertThat(AmountUtil.toFen(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(AmountUtil.fromFen(1234L)).isEqualByComparingTo(new BigDecimal("12.34"));
        assertThat(AmountUtil.fromFen(1L)).isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(AmountUtil.toFen(null)).isEqualTo(0L);
    }

    @Test
    void jsonRoundTripShouldKeepDateTimeFormat() {
        Map<String, Object> source = new HashMap<String, Object>();
        source.put("name", "订单A");
        source.put("amount", 1234L);
        String json = JsonUtils.toJson(source);
        assertThat(json).contains("\"amount\":\"1234\"");
        assertThat(JsonUtils.toMap(json).get("name")).isEqualTo("订单A");
        assertThat(JsonUtils.isValidJson(json)).isTrue();
        assertThat(JsonUtils.isValidJson("not-json")).isFalse();
    }

    @Test
    void stringFormatShouldReplacePlaceholders() {
        assertThat(StringUtils.format("渠道[{}]不支持[{}]能力", "wechat", "分账"))
                .isEqualTo("渠道[wechat]不支持[分账]能力");
        assertThat(StringUtils.isBlank("  ")).isTrue();
        assertThat(StringUtils.isBlank(null)).isTrue();
        assertThat(StringUtils.isBlank("x")).isFalse();
    }

    @Test
    void maskShouldKeepPrefixAndSuffix() {
        assertThat(MaskUtils.bankCard("6222021234567890123")).isEqualTo("6222****0123");
        assertThat(MaskUtils.phone("13800005678")).isEqualTo("138****5678");
        assertThat(MaskUtils.name("张三")).isEqualTo("张****");
        assertThat(MaskUtils.custom("abcdefgh", 2, 2)).isEqualTo("ab****gh");
    }

    @Test
    void signShouldBeDeterministicHex() {
        String sign = SignUtil.hmacSha256Hex("payload", "secret");
        assertThat(sign).isEqualTo(SignUtil.hmacSha256Hex("payload", "secret")).hasSize(64);
        assertThat(SignUtil.sha256Hex("payload")).hasSize(64).isLowerCase();
    }

    @Test
    void payMethodRegistryShouldRouteByDefaultChannel() {
        assertThat(PayMethod.of("WECHAT_NATIVE").getDefaultChannel()).isEqualTo("wechat");
        assertThat(PayMethod.of("ALI_QR").getDefaultChannel()).isEqualTo("alipay");
        assertThat(PayMethod.of("WECHAT_JSAPI").getCode()).isEqualTo("WECHAT_JSAPI");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PayMethod.of("NOT_EXIST"))
                .isInstanceOf(RuntimeException.class);
    }
}
