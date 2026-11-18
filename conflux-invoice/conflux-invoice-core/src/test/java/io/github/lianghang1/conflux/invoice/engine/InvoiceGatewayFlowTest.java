package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.invoice.api.InvoiceQueryRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceRequest;
import io.github.lianghang1.conflux.invoice.api.InvoiceResult;
import io.github.lianghang1.conflux.invoice.channel.ChannelConfig;
import io.github.lianghang1.conflux.invoice.channel.ChannelInstance;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelProvider;
import io.github.lianghang1.conflux.invoice.channel.InvoiceContext;
import io.github.lianghang1.conflux.invoice.channel.InvoiceQuerier;
import io.github.lianghang1.conflux.invoice.channel.InvoiceIssuer;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.ChannelTier;
import io.github.lianghang1.conflux.invoice.model.InvoiceItem;
import io.github.lianghang1.conflux.invoice.model.InvoiceState;
import io.github.lianghang1.conflux.invoice.model.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发票 core 编排层单元测试（不依赖 Spring / channels / example）：
 * 用纯 Java 桩渠道验证「组装载体 → 路由 → 调渠道 → 归一返回」的无状态链路，
 * 并覆盖多实例择优路由、载体回填与能力缺失的明确报错。
 */
class InvoiceGatewayFlowTest {

    private static final String APP = "app-test";
    private static final String TAX = "91310000TEST000001";
    private static final String STUB = "stub";

    @Test
    void issueShouldRouteToBestInstanceAndBackfillCarrier() {
        StubConfigProvider config = new StubConfigProvider();
        // 低优先级 + 高优先级两个实例，断言路由选高优先级
        config.instances.add(instance("low", ChannelTier.SERVICE_API, 1));
        config.instances.add(instance("high", ChannelTier.DIRECT_TAX, 10));

        InvoiceGateway gateway = InvoiceGateway.builder()
                .channel(new StubProvider())
                .configProvider(config)
                .build();

        InvoiceResult result = gateway.issue(new InvoiceRequest()
                .setAppId(APP).setBizOrderNo("INV-CORE-1")
                .setSellerTaxNo(TAX).setBuyerTitle("测试购方")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setItems(Collections.singletonList(new InvoiceItem()
                        .setItemName("服务").setAmount(new BigDecimal("100.00"))))
                .setAmount(new BigDecimal("100.00")));

        // 归一为渠道受理态；载体由结果返回（SDK 不落库）
        assertThat(result.getState()).isEqualTo(InvoiceState.PROCESSING);
        assertThat(result.getChannelOrderNo()).isEqualTo("CH-STUB");
        assertThat(result.getInvoice()).isNotNull();
        assertThat(result.getInvoice().getBizOrderNo()).isEqualTo("INV-CORE-1");
        assertThat(result.getInvoice().getChannelInstanceId()).isEqualTo("high");
    }

    @Test
    void issueWithPreferredChannelShouldRouteWithinThatChannel() {
        StubConfigProvider config = new StubConfigProvider();
        config.instances.add(instance("a", ChannelTier.SERVICE_API, 5));

        InvoiceGateway gateway = InvoiceGateway.builder()
                .channel(new StubProvider())
                .configProvider(config)
                .build();

        InvoiceResult result = gateway.issue(new InvoiceRequest()
                .setAppId(APP).setBizOrderNo("INV-CORE-2")
                .setSellerTaxNo(TAX).setBuyerTitle("测试购方")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setChannelCode(STUB)
                .setItems(Collections.singletonList(new InvoiceItem()
                        .setItemName("服务").setAmount(new BigDecimal("50.00"))))
                .setAmount(new BigDecimal("50.00")));
        assertThat(result.getState()).isEqualTo(InvoiceState.PROCESSING);
        assertThat(result.getInvoice().getChannelCode()).isEqualTo(STUB);
    }

    @Test
    void queryShouldCarryInvoiceCarrierAndNormalize() {
        StubConfigProvider config = new StubConfigProvider();
        config.instances.add(instance("a", ChannelTier.SERVICE_API, 5));
        InvoiceGateway gateway = InvoiceGateway.builder()
                .channel(new StubProvider())
                .configProvider(config)
                .build();

        InvoiceResult result = gateway.query(new InvoiceQueryRequest()
                .setAppId(APP).setChannelCode(STUB)
                .setInvoice(new io.github.lianghang1.conflux.invoice.model.Invoice()
                        .setInvoiceId("INV-CORE-3").setBizOrderNo("INV-CORE-3")
                        .setChannelOrderNo("CH-EXIST")));

        assertThat(result.getState()).isEqualTo(InvoiceState.PROCESSING);
        assertThat(result.getChannelOrderNo()).isEqualTo("CH-QUERY");
        assertThat(result.getInvoice().getInvoiceId()).isEqualTo("INV-CORE-3");
    }

    @Test
    void missingIssuerCapabilityShouldFailClearly() {
        StubConfigProvider config = new StubConfigProvider();
        config.instances.add(instance("a", ChannelTier.SERVICE_API, 5));
        // Provider 不提供 issuer → 编排层应抛明确能力缺失错误，而非空指针
        InvoiceChannelProvider noIssuer = new InvoiceChannelProvider() {
            @Override public String channelCode() { return STUB; }
            @Override public ChannelTier channelTier() { return ChannelTier.SERVICE_API; }
            @Override public Set<InvoiceType> supportedTypes() {
                return EnumSet.of(InvoiceType.DIGITAL_VAT_GENERAL);
            }
        };
        InvoiceGateway gateway = InvoiceGateway.builder()
                .channel(noIssuer).configProvider(config).build();

        assertThatThrownBy(() -> gateway.issue(new InvoiceRequest()
                .setAppId(APP).setBizOrderNo("INV-CORE-4")
                .setSellerTaxNo(TAX).setBuyerTitle("测试购方")
                .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
                .setItems(Collections.singletonList(new InvoiceItem()
                        .setItemName("服务").setAmount(new BigDecimal("10.00"))))
                .setAmount(new BigDecimal("10.00"))))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).getErrorCode())
                .isEqualTo(InvoiceErrorCode.CHANNEL_CAPABILITY_NOT_SUPPORTED);
    }

    // ---- 纯 Java 桩 ----

    private static ChannelInstance instance(String id, ChannelTier tier, int priority) {
        ChannelInstance inst = new ChannelInstance()
                .setInstanceId(id).setAppId(APP).setChannelCode(STUB)
                .setAccountKey(STUB + "#" + TAX).setSellerTaxNo(TAX)
                .setTier(tier).setPriority(priority).setEnabled(true);
        ChannelConfig cfg = new ChannelConfig()
                .setAppId(APP).setChannelCode(STUB).setChannelInstanceId(id)
                .setAccountKey(inst.getAccountKey()).setSellerTaxNo(TAX).setTier(tier);
        return inst.setConfig(cfg);
    }

    private static final class StubProvider implements InvoiceChannelProvider {
        @Override public String channelCode() { return STUB; }
        @Override public ChannelTier channelTier() { return ChannelTier.SERVICE_API; }
        @Override public Set<InvoiceType> supportedTypes() {
            return EnumSet.of(InvoiceType.DIGITAL_VAT_GENERAL);
        }
        @Override public InvoiceIssuer issuer() {
            return ctx -> InvoiceResult.accepted("CH-STUB", false);
        }
        @Override public InvoiceQuerier querier() {
            return ctx -> InvoiceResult.accepted("CH-QUERY", false);
        }
    }

    private static final class StubConfigProvider implements InvoiceConfigProvider {
        private final List<ChannelInstance> instances = new java.util.ArrayList<ChannelInstance>();

        @Override public ChannelConfig getConfig(String appId, String channelCode, String sellerTaxNo) {
            for (ChannelInstance i : instances) {
                if (i.isEnabled()) { return i.getConfig(); }
            }
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING, "no config");
        }

        @Override public ChannelConfig getByAccountKey(String channelCode, String accountKey) {
            for (ChannelInstance i : instances) {
                if (accountKey.equals(i.getAccountKey())) { return i.getConfig(); }
            }
            throw new GatewayException(InvoiceErrorCode.CONFIG_MISSING, "no config");
        }

        @Override public List<ChannelInstance> instances(String appId, String sellerTaxNo) {
            return instances;
        }
    }
}
