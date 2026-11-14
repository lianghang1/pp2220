package io.github.lianghang1.conflux.common;

import io.github.lianghang1.conflux.common.exception.CommonErrorCode;
import io.github.lianghang1.conflux.common.exception.ErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.common.http.HttpResponse;
import io.github.lianghang1.conflux.common.http.HttpTransport;
import io.github.lianghang1.conflux.common.http.HttpTransportException;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.SignUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * common 公共基座回归：错误码三段分层、JsonUtils 中立异常、HTTP 值对象、
 * 传输层 IO 中立化、签名与断言工具。零业务领域依赖。
 */
class CommonCoreTest {

    @Test
    void errorCodeSegments_areWellFormed() {
        // 1xxx 通用段
        assertThat(CommonErrorCode.BAD_REQUEST.getCode()).isEqualTo(1000);
        assertThat(CommonErrorCode.SYSTEM_ERROR.getCode()).isEqualTo(1500);
        assertThat(CommonErrorCode.BAD_REQUEST).isInstanceOf(ErrorCode.class);
        assertThat(CommonErrorCode.BAD_REQUEST.getHttpStatus()).isEqualTo(400);
    }

    @Test
    void gatewayAssert_param_usesCommonErrorCode() {
        assertThatThrownBy(() -> GatewayAssert.param(false, "参数错误"))
                .isInstanceOf(GatewayException.class)
                .satisfies(e -> assertThat(((GatewayException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.BAD_REQUEST));
    }

    @Test
    void jsonUtils_throwsIllegalArgumentException_onMalformedJson() {
        // JsonUtils 属于 common，序列化失败不得抛任何业务领域异常
        assertThatThrownBy(() -> JsonUtils.readTree("{not-json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(JsonUtils.isValidJson("{\"a\":1}")).isTrue();
        assertThat(JsonUtils.isValidJson("{not-json")).isFalse();
        assertThat(JsonUtils.toJson(new LinkedHashMap<String, Object>())).isEqualTo("{}");
    }

    @Test
    void httpResponse_exposesRawStatusBodyHeaders_withoutBusinessJudgement() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        HttpResponse ok = new HttpResponse(200, "{\"ok\":true}", headers);
        assertThat(ok.isSuccessful()).isTrue();
        assertThat(ok.getStatusCode()).isEqualTo(200);
        assertThat(ok.getBody()).isEqualTo("{\"ok\":true}");
        assertThat(ok.getHeader("content-type")).isEqualTo("application/json"); // 忽略大小写
        assertThat(ok.getHeader("missing")).isNull();

        // 非 2xx 也正常返回，不抛异常（业务判定留给渠道层）
        HttpResponse fail = new HttpResponse(503, "down", null);
        assertThat(fail.isSuccessful()).isFalse();
        assertThat(fail.getBody()).isEqualTo("down");
        assertThat(fail.getHeaders()).isEmpty();
        HttpResponse nullBody = new HttpResponse(204, null, null);
        assertThat(nullBody.getBody()).isEmpty();
    }

    @Test
    void httpTransport_neutralizesInvalidUrl() {
        // 非法 URL 被中立化为 HttpTransportException（非业务错误码异常），保留 cause 信息
        assertThatThrownBy(() -> HttpTransport.get("not-a-valid-url", null))
                .isInstanceOf(HttpTransportException.class);
    }

    @Test
    void signUtil_hmacSha256_isStable() {
        String hex = SignUtil.hmacSha256Hex("hello", "secret");
        assertThat(hex).isNotBlank().hasSize(64);
        assertThat(SignUtil.hmacSha256Hex("hello", "secret")).isEqualTo(hex);
        assertThat(SignUtil.hmacSha256Hex("hello", "other")).isNotEqualTo(hex);
    }
}
