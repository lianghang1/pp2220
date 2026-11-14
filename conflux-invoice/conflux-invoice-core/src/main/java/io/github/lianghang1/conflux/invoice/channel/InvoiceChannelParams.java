package io.github.lianghang1.conflux.invoice.channel;

/**
 * 发票渠道参数契约：各渠道包定义自己的参数类型，承载企业准入凭据
 * （appKey/appSecret、税号备案信息、网关地址、证书等）。
 *
 * <p>框架对参数内容零感知：渠道包在自己的能力实现里向下转型；类型不匹配时按
 * {@code CONFIG_MISSING} 快速暴露配置错装。</p>
 *
 * <p>{@link #channelAppId()} 与 {@link #sellerTaxNo()} 共同构成回调配置反查的输入，
 * 经 {@link InvoiceAccountKey#of(String, String)} 生成唯一账户键。</p>
 *
 * @author lianghang
 */
public interface InvoiceChannelParams {

    /** 渠道侧应用标识（如服务商分配的 appKey / 企业编码）。 */
    String channelAppId();

    /** 该配置对应的销方纳税人识别号（企业准入标识）。 */
    default String sellerTaxNo() {
        return null;
    }
}
