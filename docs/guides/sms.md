# 短信域使用指南

短信域内部**全量委托 [sms4j](https://github.com/dromara/SMS4J) 原始包**（api/core/provider，不用它的 starter），我们只保留两层：配置装载与出口门面。**不含任何渠道实现代码**。通用契约见 [getting-started.md](../getting-started.md)。

## 1. 引入

### Spring Boot starter（推荐）

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>conflux-sms-spring-boot-starter</artifactId>
    <version>请替换为实际版本</version>
</dependency>
```

starter 自动装配 `SmsGateway`。sms4j 官方 Factory 装载渠道配置；内置 22 家厂商由 sms4j 自动发现，无需声明。

### 纯 jar / Spring MVC

引入 `conflux-sms-core`（经 core 传递引入 sms4j 原始包），core 零 Spring 依赖：

```java
SmsConfigProvider provider = new InMemorySmsConfigProvider()
        .register("demo", "alibaba",            // configId / channelCode
                  "accessKeyId", "accessKeySecret", "signature", "templateId",
                  null);                         // 厂商特有键放 attr（Map，可为 null）
SmsGateway gateway = SmsGateway.builder()
        .configProvider(provider)
        .build();                                // 内置渠道自动发现，无需 .channel()
```

## 2. 配置

配置路径 `conflux.sms.configs.<configId>.<channelCode>`：

```yaml
conflux:
  sms:
    configs:
      demo:
        alibaba:                   # sms4j 供应商标识
          # 公共凭证四件套（SmsChannelConfig 一等字段，跨渠道同名）
          access-key-id: xxx
          access-key-secret: xxx
          signature: 你的签名
          template-id: SMS_xxx
          attr:                   # 厂商特有键，键名与 sms4j Config 字段一致
            template-name: code
            region-id: cn-hangzhou
```

- **公共凭证四件套**：`accessKeyId` / `accessKeySecret` / `signature` / `templateId` 是自有 `SmsChannelConfig` 的一等字段。装载期由 `mergedAttr()` 合并（公共字段优先、attr 兜底），再由 sms4j 官方 Factory 按字段名构建厂商 Config。**不暴露 sms4j 官方配置类**。
- **`channelCode`** 直接采用 sms4j 的供应商标识（`alibaba` / `tencent` / `huawei` …）；厂商特有键（腾讯 `sdkAppId`、华为 `sender`、极光 `signId` 等）放 `attr`，键名用 sms4j Config 的字段名即可。
- 配置源每次读取，凭证变更可重建客户端；接 DB / 配置中心只需自定义 `SmsConfigProvider`：

```java
SmsConfigProvider dbProvider = new SmsConfigProvider() {
    public SmsChannelConfig getConfig(String configId, String channelCode) { /* 查库 */ ... }
};
SmsGateway gateway = SmsGateway.builder().configProvider(dbProvider).build();
```

## 3. 发送

```java
SmsSendResult r = smsGateway.send("demo", "alibaba",
        new SmsSendRequest().setBizId("B-1").setPhone("18888888888")
                .setParams(Collections.singletonMap("code", "123456")));
```

- `SmsSendRequest`：`bizId`（业务标识）、`phone`、`content`、`params`（模板变量；非空走 Map 重载，否则走文本重载）。
- `SmsSendResult`：`success`、`bizId`、`channel`（渠道编码）、`configId`、`raw`（sms4j 原始返回）、`error`。
- **发送即终态**，SDK 不持有台账、不判重、不做频率控制。

## 4. 自定义厂商（sms4j 官方扩展）

内置 22 家渠道开箱即用（标识见 sms4j 官方清单）。接入清单外厂商完全走 [sms4j 官方自定义扩展](https://sms4j.com/doc3/custom.html)：按文档实现 `SmsBlend` / `SupplierConfig` / `BaseProviderFactory` 三个接口，把 Factory 实例交给 `SmsChannelProvider.of(factory)` 声明即可，无需改框架代码：

```java
BaseProviderFactory myFactory = new MyProviderFactory();   // sms4j 官方三接口实现
SmsGateway gateway = SmsGateway.builder()
        .configProvider(provider)
        .channel(SmsChannelProvider.of(myFactory))          // 仅自定义渠道需要声明
        .build();
```

运行期也可用 `gateway.registerChannel(provider)` 增补、`gateway.registeredChannels()` 查看已注册渠道。

## 5. 排错

- `CONFIG_MISSING`：确认 `configId` + `channelCode` 与配置一致（内存实现按 `configId#channelCode` 索引）。
- 发送报参数/签名错误：厂商特有键是否用了 sms4j Config 的真实字段名（放 `attr`）；公共四件套是否齐全。
- `raw` 里有原始错误：查 sms4j 对应厂商的返回码含义，SDK 不改写第三方错误。
- 自定义厂商不生效：确认 Factory 已通过 `SmsChannelProvider.of(factory)` 声明，且 `configId` + `channelCode` 匹配。

> sms4j 版本在 BOM（`conflux-dependencies`）单源管理。
