package com.sfi.conflux.pay.api;

import com.sfi.conflux.pay.model.PayPayloadType;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 支付唤起参数：告诉使用方「如何唤起用户支付」。
 *
 * <p>content 语义由 type 决定（码值 / URL / JSON 参数串），
 * 见 {@link PayPayloadType}。所有方法线程安全（不可变使用）。</p>
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class PayPayload {

    /** 参数类型 */
    private PayPayloadType type;
    /** 参数内容（码值/URL/JSON） */
    private String content;

    /** 二维码支付参数。 */
    public static PayPayload qrCode(String content) {
        return new PayPayload().setType(PayPayloadType.QRCODE).setContent(content);
    }

    /** 跳转支付参数。 */
    public static PayPayload redirect(String url) {
        return new PayPayload().setType(PayPayloadType.REDIRECT).setContent(url);
    }

    /** JSAPI/小程序唤起参数（content 为 JSON 对象串）。 */
    public static PayPayload jsapi(String json) {
        return new PayPayload().setType(PayPayloadType.JSAPI).setContent(json);
    }

    /** APP 唤起参数（content 为 JSON 对象串）。 */
    public static PayPayload app(String json) {
        return new PayPayload().setType(PayPayloadType.APP).setContent(json);
    }

    /** 无唤起参数（即时扣款类支付方式）。 */
    public static PayPayload none() {
        return new PayPayload().setType(PayPayloadType.NONE).setContent(null);
    }
}
