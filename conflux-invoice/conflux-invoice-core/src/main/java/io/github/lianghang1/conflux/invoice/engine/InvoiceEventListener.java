package io.github.lianghang1.conflux.invoice.engine;

/**
 * 发票事件监听端口：宿主可实现后注册到 {@link InvoiceRuntime}。
 *
 * <p>发布方对监听器异常做隔离，单个监听器失败不影响主流程。</p>
 *
 * @author lianghang
 */
public interface InvoiceEventListener {

    void onEvent(InvoiceEvent event);
}
