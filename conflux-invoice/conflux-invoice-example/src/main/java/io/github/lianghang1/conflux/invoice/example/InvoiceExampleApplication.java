package io.github.lianghang1.conflux.invoice.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 发票网关演示宿主：starter 自动装配 + 内存 Store + REST 入口，默认 mock 渠道，
 * 无需任何凭据或网络即可跑通「开票 → 授权 → XML 归档 → 红字申请 → 确认 → 开红票」。
 *
 * @author lianghang
 */
@SpringBootApplication
public class InvoiceExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceExampleApplication.class, args);
    }
}
