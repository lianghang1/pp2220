package io.github.lianghang1.conflux.pay.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 演示宿主启动类：展示通过 starter 接入支付网关的最小工程形态——
 * 注入网关 Bean、暴露回调入口即可。SDK 无台账，落库、查重与兜底查单由使用方自行实现。
 */
@SpringBootApplication
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
