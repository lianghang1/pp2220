package io.github.lianghang1.paygateway.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 演示宿主启动类：展示通过 starter 接入支付网关的最小工程形态——
 * 使用方只需实现 Store Bean、暴露回调入口、挂接兜底定时任务。
 */
@EnableScheduling
@SpringBootApplication
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
