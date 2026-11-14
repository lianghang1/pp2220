package io.github.lianghang1.conflux.invoice.channel;

import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;

import java.util.Map;
import java.util.Set;

/**
 * 发票渠道提供者 SPI：一个渠道实现的统一入口（Ports &amp; Adapters）。
 *
 * <p>实现方声明渠道编码、通道级别与支持的票种，并按能力选择性暴露处理器
 * （不支持的能力返回 null，注册表按能力查找、缺失时报明确异常）。
 * 渠道专属 SDK、签名、协议、字段映射全部封闭在渠道包内部。</p>
 *
 * <p><b>实现要求</b>：实例无状态、线程安全（所有方法可能被并发调用）；
 * 运行态数据一律从 {@link InvoiceContext} 取得，不得在 Provider 上保存单据状态。</p>
 *
 * @author lianghang
 */
public interface InvoiceChannelProvider {

    /** 渠道编码（全局唯一，如 mock / baiwangjs / qixiangyun）。 */
    String channelCode();

    /** 通道级别（决定路由优先级）。 */
    ChannelTier channelTier();

    /** 支持的票种；返回空集合表示不限制。 */
    Set<InvoiceType> supportedTypes();

    /**
     * 把配置键值对解析为渠道参数对象；不支持声明式配置的渠道返回 null
     * （此时使用方必须自行实现 {@code InvoiceConfigProvider}）。
     */
    default InvoiceChannelParams parseParams(Map<String, String> properties) {
        return null;
    }

    /** 蓝字开票能力。 */
    default InvoiceIssuer issuer() {
        return null;
    }

    /** 查单/轮询能力。 */
    default InvoiceQuerier querier() {
        return null;
    }

    /** 红字两段能力。 */
    default InvoiceRedHandler redHandler() {
        return null;
    }

    /** 版式文件下载能力。 */
    default InvoiceFileFetcher fileFetcher() {
        return null;
    }

    /** 授权会话能力。 */
    default AuthSessionHandler authHandler() {
        return null;
    }

    /** 回调解析能力。 */
    default InvoiceCallbackHandler callbackHandler() {
        return null;
    }
}
