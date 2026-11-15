package io.github.lianghang1.conflux.invoice.engine;

import io.github.lianghang1.conflux.common.exception.GatewayAssert;
import io.github.lianghang1.conflux.invoice.channel.InvoiceChannelRegistry;
import io.github.lianghang1.conflux.invoice.config.InvoiceConfigProvider;
import io.github.lianghang1.conflux.invoice.exception.InvoiceErrorCode;
import io.github.lianghang1.conflux.invoice.model.Invoice;
import io.github.lianghang1.conflux.invoice.model.RedApply;
import io.github.lianghang1.conflux.invoice.store.AuthSessionStore;
import io.github.lianghang1.conflux.invoice.store.InvoiceFileStore;
import io.github.lianghang1.conflux.invoice.store.InvoiceStore;
import io.github.lianghang1.conflux.invoice.store.RedApplyStore;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 发票网关运行时上下文：聚合全部端口（渠道注册表、配置提供者、四类 Store）与事件监听器。
 *
 * <p>零 Spring 依赖，纯 jar 手动装配与 starter 自动装配共用同一个运行时对象。
 * 时间来源抽成 {@link Clock}，便于测试注入固定时钟验证会话过期、租约、确认时限。</p>
 *
 * @author lianghang
 */
@Slf4j
public class InvoiceRuntime {

    public final InvoiceChannelRegistry registry;
    public final InvoiceConfigProvider configProvider;
    public final InvoiceStore invoiceStore;
    public final RedApplyStore redApplyStore;
    public final AuthSessionStore authSessionStore;
    public final InvoiceFileStore fileStore;

    private final List<InvoiceEventListener> listeners =
            new CopyOnWriteArrayList<InvoiceEventListener>();
    private volatile Clock clock = Clock.systemDefaultZone();

    /** 版式文件固化选项（可整体替换；默认即时开启、不落盘）。 */
    private volatile io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions fileOptions =
            new io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions();

    public InvoiceRuntime(InvoiceChannelRegistry registry,
                          InvoiceConfigProvider configProvider,
                          InvoiceStore invoiceStore,
                          RedApplyStore redApplyStore,
                          AuthSessionStore authSessionStore,
                          InvoiceFileStore fileStore) {
        this.registry = GatewayAssert.notNull(registry, InvoiceErrorCode.STORE_MISSING,
                "InvoiceChannelRegistry 未注册");
        this.configProvider = GatewayAssert.notNull(configProvider, InvoiceErrorCode.STORE_MISSING,
                "InvoiceConfigProvider 未注册");
        this.invoiceStore = GatewayAssert.notNull(invoiceStore, InvoiceErrorCode.STORE_MISSING,
                "InvoiceStore 未注册");
        this.redApplyStore = GatewayAssert.notNull(redApplyStore, InvoiceErrorCode.STORE_MISSING,
                "RedApplyStore 未注册");
        this.authSessionStore = GatewayAssert.notNull(authSessionStore, InvoiceErrorCode.STORE_MISSING,
                "AuthSessionStore 未注册");
        this.fileStore = GatewayAssert.notNull(fileStore, InvoiceErrorCode.STORE_MISSING,
                "InvoiceFileStore 未注册");
    }

    /** 当前时间（可注入固定时钟）。 */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public InvoiceRuntime setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
        return this;
    }

    public Clock getClock() {
        return clock;
    }

    /** 版式文件固化选项（永不为 null）。 */
    public io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions getFileOptions() {
        return fileOptions;
    }

    public InvoiceRuntime setFileOptions(
            io.github.lianghang1.conflux.invoice.config.InvoiceFileOptions fileOptions) {
        if (fileOptions != null) {
            this.fileOptions = fileOptions;
        }
        return this;
    }

    public InvoiceRuntime addListener(InvoiceEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    /** 发布事件；单个监听器异常被隔离，不阻断主流程。 */
    public void publish(InvoiceEvent.Type type, Invoice invoice, RedApply redApply, String message) {
        InvoiceEvent event = new InvoiceEvent()
                .setType(type).setInvoice(invoice).setRedApply(redApply)
                .setOccurredTime(now()).setMessage(message);
        for (InvoiceEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.warn("发票事件监听器异常已隔离: type={}, listener={}",
                        type, listener.getClass().getName(), e);
            }
        }
    }
}
