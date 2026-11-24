# 通用入门

本文讲五域共通的引入形态、配置模型与回调定位。具体到某个域的调用与配置键，见 [使用指南](guides/pay.md)（[支付](guides/pay.md) · [发票](guides/invoice.md) · [通知](guides/notify.md) · [文件](guides/file.md) · [登录](guides/auth.md)）。

## 1. 核心约定（先读）

- **SDK 无状态**：结果对象只是渠道归一后的快照；票据载体（`Invoice` / `RedApply` / `AuthSession`）随结果返回、随请求回传，由使用方保管。
- **受理 ≠ 终态**：下单/开票返回的 `state` 是渠道受理态，最终态来自回调或主动查单，二者至少实现其一。
- **判重 / 金额校验 / 状态推进在使用方**：回调只做验签 + 归一。
- **业务单号是唯一线索**：`bizOrderNo` / `bizRefundNo` 等由使用方生成，作为渠道商户单号；SDK 不生成内部单号。
- **金额单位**：支付 = 分（`Long`），发票 = 元（`BigDecimal`）。

> **验签在框架，配置定位在使用方**：业务按自己的订单号查库得到 `configId`（发票再加 `sellerTaxNo`），显式传入 `parseCallback(configId, request)`；框架据此取配置验签，不隐式反查账户键。回调 URL 无需携带任何账户定位参数。

## 2. 三种引入形态

五域完全同构，任选其一：

| 环境 | 引入模块 | 装配方式 | 配置来源 |
|---|---|---|---|
| Spring Boot | `conflux-<域>-spring-boot-starter` | starter 经 `AutoConfiguration.imports` 自动装配门面 Bean | `application.yml` 或自实现配置源 |
| Spring MVC / 传统 Spring | 对应 `core`（支付/发票再加 `channels`） | 一个 `@Configuration` + `@Bean`，调用 `XxxGateway.builder()` | 内存 / DB / 配置中心 / properties |
| 纯 Java | 对应 `core`（支付/发票再加 `channels`） | 直接 `XxxGateway.builder()....build()` | 同上 |

- 依赖版本由 BOM（`conflux-dependencies`）单源管理；示例中写 `<version>请替换为实际版本</version>`。
- starter 依赖 `spring-boot-autoconfigure`，仅 Boot 可用；MVC / 纯 Java **不要引入 starter**。
- core 与 channels 零 Spring 依赖；短信/文件的 core 经传递引入第三方原始包（sms4j / x-file-storage）。

> **"SPI"与"自动装配"不是一回事**：本项目 SPI 指扩展点接口（自定义渠道 `XxxChannelProvider`、自定义配置源 `XxxConfigProvider`），三种环境完全一致；Boot 自动装配只是省去手写 `@Bean`，不改变扩展能力。

## 3. 门面契约（五域一致）

```java
XxxGateway gateway = XxxGateway.builder()
        .configProvider(cp)       // 必填，build 时校验，缺失抛 CONFIG_MISSING
        .build();                 // 内置渠道在此自动全量注册

gateway.registerChannel(provider);   // 运行期增补自定义渠道（同编码覆盖内置）
gateway.registeredChannels();        // 查看已注册渠道编码
```

- **内置渠道由 `build()` 自动全量注册**（支付/发票经 Java SPI 清单，短信/文件由第三方 SDK 自动发现），用户零 `.channel()`。
- `.channel(provider)` **仅用于自定义渠道扩展口**——框架未内置、或想用同编码覆盖内置时使用。
- 配置源每次调用读取，凭证变更可重建客户端——接 DB / 配置中心只需自定义对应的 `XxxConfigProvider`。

## 4. 配置模型（五域一致）

配置路径统一为 `conflux.<域>.configs.<configId>.<channelCode>`：

- **configId**：框架侧配置定位键（**不是**渠道真实 appId，避免与微信/支付宝的 `appId` 混淆）。
- **channelCode**：对外渠道编码；短信/文件内部再映射为第三方 SDK 的供应商标识 / 平台实例名。
- **公共字段一等化 + `attr`**：跨渠道同名的公共字段（如 `notifyUrl`、`sandbox`，短信的凭证四件套）为配置对象一等字段；渠道个性键统一放 `attr`（**单数**）。
- 配置对象：支付/发票为 `ChannelConfig` 基类 + 各渠道 `XxxParams extends ChannelConfig`；短信为 `NotificationConfig`；文件为 `FileChannelConfig`。

两种形态共用同一套模型——yml 由 starter 的 `PropertiesXxxConfigProvider` 映射到配置对象，纯 Java 用 `XxxParams.fromMap(attr)` 装配（JDK 8 用 `LinkedHashMap`，没有 `Map.of`）。

纯 Java 装配示例（支付）：

```java
Map<String, String> attr = new LinkedHashMap<String, String>();
attr.put("merchantId", "1900000001");
attr.put("appId", "wx8888888888888888");
attr.put("apiV3Key", "your-32-bytes-apiV3-key-aaaaaaaaaaaa");
PaymentGateway gateway = PaymentGateway.builder()
        .configProvider(new InMemoryChannelConfigProvider()
                .register(WechatParams.fromMap(attr)
                        .setConfigId("demo").setChannelCode("WECHAT")
                        .setNotifyUrl("https://your.host/pay/notify/wechat")))
        .build();
```

## 5. 回调接入（四域一致；登录域为换身份，见其指南）

> 登录域的两步是 `authorize`（生成授权入口）+ `parseCallback`（回调换身份），后者不验签、不落库，只做票据交换与身份归一——与其余四域的"验签+归一"语义不同，详见 [登录指南](guides/auth.md)。

1. 暴露一个回调端点（多渠道可共用）。
2. 从报文取业务单号，查自己的库得到 `configId`（发票再加 `sellerTaxNo`）。
3. 调 `parseCallback(configId, request)` 验签 + 归一。
4. 按归一事件定位订单、判重、校验金额、推进状态。
5. 用门面生成应答报文返回渠道。

SDK 不介入业务链路，不判重、不推进状态、不通知下游。

---

**下一步**：按你的能力域阅读 [支付](guides/pay.md) · [发票](guides/invoice.md) · [通知](guides/notify.md) · [文件](guides/file.md) · [登录](guides/auth.md) 指南；字段级细节查 [API 参考](reference/api-reference.md)。
