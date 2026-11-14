package io.github.lianghang1.paygateway.channels.unionpay;

import io.github.lianghang1.paygateway.channel.ChannelConfig;
import io.github.lianghang1.paygateway.exception.GatewayErrorCode;
import io.github.lianghang1.paygateway.exception.GatewayException;
import io.github.lianghang1.paygateway.util.JsonUtils;
import io.github.lianghang1.paygateway.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 云闪付 HTTP 客户端：POST JSON 报文、签名注入、respCode 业务判定。
 *
 * <p>应答约定：{@code respCode} 为 "00" 表示受理成功，"02"/"12"/"05" 表示
 * 处理中或需查询确认，其余视为失败（respMsg 描述原因）。客户端线程安全。</p>
 *
 * @author lianghang
 */
@Slf4j
public class UnionPayClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private final UnionPaySigner signer = new UnionPaySigner();

    public UnionPaySigner signer() {
        return signer;
    }

    /**
     * 发起云闪付接口调用：组装参数 → 注入签名 → POST JSON → 返回应答 Map。
     * 业务失败（respCode 非 00/02/12/05）抛渠道异常并携带 respCode/respMsg。
     */
    public Map<String, Object> execute(String action, String apiUri, ChannelConfig config,
                                       Map<String, Object> requestParams) {
        UnionPayParams params = UnionPayClient.paramsOf(config);
        Map<String, Object> signed = signer.sign(requestParams, params);
        String url = params.gateway() + apiUri;
        try (Response response = client.newCall(new Request.Builder()
                .url(url)
                .post(RequestBody.create(JsonUtils.toJsonBytes(signed), JSON))
                .build()).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            log.info("云闪付{}完成: url={}, resp={}", action, apiUri, text);
            if (!response.isSuccessful() || StringUtils.isBlank(text)) {
                throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                        "云闪付{}HTTP异常: status={}, body={}", action, response.code(), text);
            }
            Map<String, Object> result = JsonUtils.toMap(text);
            String respCode = result.get("respCode") == null ? null : String.valueOf(result.get("respCode"));
            if (isAccepted(respCode)) {
                return result;
            }
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "云闪付{}失败: respCode={}, respMsg={}",
                    action, respCode, result.get("respMsg"));
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED,
                    "云闪付{}调用失败: {}", action, e.getMessage(), e);
        }
    }

    /** 应答码是否处于「受理成功或处理中」区间。 */
    public static boolean isAccepted(String respCode) {
        return "00".equals(respCode) || "02".equals(respCode) || "12".equals(respCode) || "05".equals(respCode);
    }

    public static UnionPayParams paramsOf(ChannelConfig config) {
        if (config.getParams() instanceof UnionPayParams) {
            return (UnionPayParams) config.getParams();
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "云闪付渠道参数类型不匹配: {}", config.getParams() == null ? "null" : config.getParams().getClass().getName());
    }
}
