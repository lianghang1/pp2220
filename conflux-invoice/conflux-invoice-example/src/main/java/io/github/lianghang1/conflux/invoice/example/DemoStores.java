package io.github.lianghang1.conflux.invoice.example;

import io.github.lianghang1.conflux.invoice.store.AuthSessionStore;
import io.github.lianghang1.conflux.invoice.store.InvoiceFileStore;
import io.github.lianghang1.conflux.invoice.store.InvoiceStore;
import io.github.lianghang1.conflux.invoice.store.RedApplyStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryAuthSessionStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceFileStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryInvoiceStore;
import io.github.lianghang1.conflux.invoice.support.InMemoryRedApplyStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 演示用内存 Store 注册。
 *
 * <p><b>仅供演示与单测</b>：产品沿用支付域的边界，Store 由使用方实现并注册；
 * 生产环境请替换为自己的数据库实现（唯一索引、条件更新、租约、XML 长期留存）。
 * 这里直接复用 core 的 {@code support} 参考实现，展示契约的最小可运行形态。</p>
 *
 * @author lianghang
 */
@Configuration
public class DemoStores {

    @Bean
    public InvoiceStore invoiceStore() {
        return new InMemoryInvoiceStore();
    }

    @Bean
    public RedApplyStore redApplyStore() {
        return new InMemoryRedApplyStore();
    }

    @Bean
    public AuthSessionStore authSessionStore() {
        return new InMemoryAuthSessionStore();
    }

    @Bean
    public InvoiceFileStore invoiceFileStore() {
        return new InMemoryInvoiceFileStore();
    }
}
