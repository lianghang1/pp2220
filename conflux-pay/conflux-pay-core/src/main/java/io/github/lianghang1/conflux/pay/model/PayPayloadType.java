package io.github.lianghang1.conflux.pay.model;

/**
 * 支付唤起参数类型：向使用方表达「拿到结果后该如何唤起支付」。
 *
 * @author lianghang
 */
public enum PayPayloadType {

    /** 二维码内容（content 为码值，使用方自行渲染二维码） */
    QRCODE,
    /** 跳转地址（content 为 URL，使用方引导用户跳转） */
    REDIRECT,
    /** JSAPI 唤起参数（content 为 JSON 对象，如公众号/小程序支付参数） */
    JSAPI,
    /** APP 唤起参数（content 为 JSON 对象） */
    APP,
    /** 无唤起参数（付款码等即时扣款方式，下单即返回支付结果） */
    NONE
}
