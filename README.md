# pay-gateway

可嵌入的聚合支付网关框架（JDK 8+）。

## 特性

- **统一支付 API**：支付、查询、关闭、退款、转账、分账，一套出入参模型
- **多渠道适配**：渠道实现集中单模块按包隔离，渠道专属 SDK/签名/报文封闭在各自包内；新增渠道零改内核
- **零表结构**：框架不建表、不绑存储，持久化通过 Store SPI 交给使用方实现（订单、通知、对账差错等记录全部自定义落库）
- **双形态引入**：任意 Java 应用直接引 core jar 手动组装；Spring Boot 应用引 starter 自动装配
- **异步通知**：渠道回调验签解析 → 统一状态机迁移 → 事件发布 → 下游 HTTP 通知（分级退避重试 + 通知留痕）
- **对账**：渠道账单与本地订单比对，差错分类留痕、状态自动修正

## 模块

| 模块 | 说明 |
|---|---|
| pay-gateway-core | 渠道无关内核：模型、状态机、SPI、引擎，零 Spring 依赖 |
| pay-gateway-channels | 全部渠道实现（mock / wechat / alipay / unionpay / paypal），按包隔离 |
| pay-gateway-spring-boot-starter | Spring Boot 2.x 自动装配，`pay-gateway.*` 配置绑定 |
| pay-gateway-example | 演示宿主：starter 接入 + 使用方自行实现 Store + 回调 Controller |

## 快速开始

（接入指南、Store 实现示例、渠道扩展指南整理中，见后续文档章节）
