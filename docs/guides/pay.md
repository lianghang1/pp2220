# 支付域使用指南

无状态渠道聚合层：统一方法名、出入参、结果、回调验签归一。落库、判重、金额校验、状态推进、下游通知由使用方完成。

> 受理态 ≠ 终态，最终态来自回调或主动查单；`bizOrderNo` 贯穿全程。通用契约见 [getting-started.md](../getting-started.md)。

## 1. 引入

### Spring Boot starter（推荐）

引入 `conflux-pay-spring-boot-starter`，starter 经 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动装配 `PaymentGateway` 单例 Bean。**内置渠道（mock / 微信 / 支付宝 / 云闪付 / PayPal / 天满）由 `build()` 经 SPI 清单全量自动注册，用户零配置即可使用**；自定义的 `ChannelProvider` Bean 按同编码覆盖内置渠道。

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>conflux-pay-spring-boot-starter</artifactId>
    <version>请替换为实际版本</version>
</dependency>
```

注入即可用：`@Autowired private PaymentGateway paymentGateway;`

### 纯 jar / Spring MVC

用 `PaymentGateway.builder()` 装配，零 Spring 依赖；内置渠道由 `build()` 自动注册，无需逐个 `.channel()`。

```java
// 方式 A：内置内存实现（配置来自代码 / 使用方自管）
InMemoryChannelConfigProvider configs = new InMemoryChannelConfigProvider()
        .register(WechatParams.fromMap(attr)                 // 渠道强类型配置，公共字段一等化
                .setConfigId("demo").setChannelCode("WECHAT")
                .setNotifyUrl("https://your.host/pay/notify/wechat"));

// 方式 B：自行实现 ChannelConfigProvider（DB / 配置中心读取，每次调用读取）
ChannelConfigProvider configs = /* 使用方实现 */;

PaymentGateway gateway = PaymentGateway.builder()
        .configProvider(configs)
        .build();                                            // 内置渠道在此自动注册
```

`builder()` 必须设置 `configProvider`；内置渠道无需声明。运行期可用 `gateway.registerChannel(provider)` 增补自定义渠道，`gateway.registeredChannels()` 查看已注册渠道。

> 配置模型：公共字段（`configId` / `channelCode` / `notifyUrl` / `sandbox`）为一等字段，渠道个性参数（服务商模式 `subMchNo`、微信 `merchantId` 等）放 `attr`，由各渠道 `XxxParams.fromMap(attr)` 解析。JDK 8 用 `LinkedHashMap` 装配 `attr`，没有 `Map.of`。

## 2. 配置

Spring Boot 配置路径 `conflux.pay.configs.<configId>.<channelCode>`，例：

```yaml
conflux.pay:
  configs:
    demo:
      wechat:
        notifyUrl: https://your.host/pay/callback/wechat
        sandbox: false
        attr:                     # 渠道个性键，服务商模式 subMchNo 也放这里
          merchantId: "1900000001"
          appId: "wx8888888888888888"
          apiV3Key: "your-32-bytes-apiV3-key-aaaaaaaaaaaa"
          merchantSerialNumber: "5157F09EFDC096DE15EBE81A47057A72..."
          merchantPrivateKey: "/data/cert/apiclient_key.pem"
```

各渠道完整的配置键（必填项、含义、服务商模式）见 [配置键对照表](../reference/config-keys.md)。

## 3. 核心调用

```java
// 1) 下单：method 决定路由渠道，bizOrderNo 由使用方生成
PaymentResult pay = gateway.createPayment(new PaymentRequest()
        .setConfigId("demo").setBizOrderNo("ORDER_001")
        .setMethod(PayMethod.WECHAT_NATIVE.getCode())
        .setAmount(9900L).setSubject("订单支付"));
// state=PAYING → pay.getPayPayload() 拉起支付；SUCCEEDED → 即时扣款已完成
// channelOrderNo 由使用方保存（SDK 不存）

// 2) 查单：最终态兜底（回调丢失时）
PaymentResult snapshot = gateway.queryPayment(new PaymentQueryRequest()
        .setConfigId("demo").setBizOrderNo("ORDER_001")
        .setChannelCode(ChannelCode.WECHAT).setChannelOrderNo(channelOrderNo));

// 3) 退款 / 转账 / 分账：原单要素由使用方随请求提供
RefundResult refund = gateway.createRefund(new RefundRequest()
        .setConfigId("demo").setBizRefundNo("REFUND_001")
        .setChannelCode(ChannelCode.WECHAT).setBizOrderNo("ORDER_001")
        .setTotalAmount(9900L).setAmount(9900L));
```

**金额单位统一为分（`Long`）**。可退余额校验、判重、状态推进都是业务的事，SDK 不校验。`notifyUrl` 默认取渠道配置，也可在支付/退款/转账请求里用 `.setNotifyUrl(...)` 按单覆盖（请求优先、配置兜底）。

支付方式（`method`）与渠道的映射见 `PayMethod` 注册表；下单也可用 `channelCode` 显式覆盖路由。

## 4. 回调接入

SDK 只做验签 + 归一。验签仍在框架，但**配置定位权在使用方**：业务按渠道单号查自己的库得到 `configId`，显式传给 `parseCallback(configId, request)`；框架据此取配置验签，不隐式反查账户键。

```java
@PostMapping("/callback/{channelCode}")
public String callback(@PathVariable String channelCode,
                       HttpServletRequest req) {
    // 1) 从报文取渠道单号，查自己 DB 定位 configId
    String configId = /* 按 bizOrderNo / channelOrderNo 查库得到 */;

    CallbackEvent event = paymentGateway.parseCallback(configId,
            new ChannelCallbackRequest()
                    .setChannelCode(channelCode)
                    .setHeaders(/* 请求头 */)
                    .setQueryParams(/* query */)
                    .setFormParams(/* form */)
                    .setBody(/* 原始 body */));

    // 2) 定位订单 → 判重 → 校验金额 → 推进状态（SDK 不介入）
    return paymentGateway.callbackSuccessReply(channelCode); // 异常用 callbackFailureReply
}
```

回调 URL 无需携带任何账户定位参数；多渠道/多实例共用一个回调端点即可，靠业务单号查库区分。

### 天满渠道（chinaums）的回调说明

天满通知报文中带 `sign`，官方给出了基于通讯密钥的验签算法；但本 SDK 采用更强的**回调触发主动查单确认**：回调处理器用报文里的 `merOrderId` 作为查单参数，以 OPEN-BODY-SIG 双向认证调用 `/v1/netpay/query` 获取权威状态构造事件，不信任通知内容里的状态与金额。验签只能证明报文来源，查单才是渠道当前真实状态，故以查询接口为准——这也符合官方文档「以查询接口响应的订单状态更新订单状态」的推荐。通讯密钥与产品密钥（`appKey`）是否相同未经真实凭证验证，本 SDK 不臆造验签实现。

因此天满回调需：业务从报文的 `merOrderId`（商户订单号）查自己的库得到 `configId`，显式传入 `parseCallback(configId, request)`；使用方可放心以返回的 `CallbackEvent` 推进状态，无需自行验签。应答报文：成功 `SUCCESS`、失败 `FAILED`（官方「支付结果通知」规则）。

## 5. 进阶能力

- **退款 / 转账 / 分账 / 关单 / 查单**：见 [API 参考](../reference/api-reference.md) 的方法清单与出入参字段。
- **账单**：`fetchBill(channelCode, configId, billDate)` 拉取渠道账单，对账比对由使用方完成。
- **微信服务商模式**：`subMchNo` 等放 `attr`，多个特约商户用不同 `configId` 各自一份配置，见 [配置键对照表](../reference/config-keys.md#服务商模式配置示例)。

## 6. 接入自定义渠道（SPI）

新增渠道不用改内核，实现 `ChannelProvider` 及所需能力 Handler，再注册即可。

1. **实现 `ChannelProvider`**：声明 `channelCode()`、`supportedMethods()`；按需要提供 `paymentHandler()` / `refundHandler()` / `transferHandler()` / `profitShareHandler()` / `billHandler()` / `callbackHandler()`（均为 default 方法，只实现支持的能力）。
2. **实现能力 Handler**：如 `PaymentHandler.createPayment/create...`，返回值即归一结果；渠道 SDK/签名/报文封闭在渠道包内。
3. **注册支付方式**：启动期用 `PayMethod.register("MY_METHOD", "mychannel")` 注册「方式编码 → 默认渠道」映射；下单传 `method="MY_METHOD"` 即路由到你的渠道。
4. **注册渠道**：starter 场景把 `ChannelProvider` 声明为 Spring Bean（同编码覆盖内置）；纯 jar 场景用 `builder().channel(new MyChannelProvider())` 或 `gateway.registerChannel(...)`。
5. **配置键**：按 [配置键对照表](../reference/config-keys.md) 在 `conflux.pay.configs.<configId>.<渠道编码>` 下声明；公共字段为一等字段，渠道个性键放 `attr`。

## 7. 排错

真实账号到位后的逐项联调见 [联调检查清单](../integration-checklist.md)。常见问题：验签失败（头/表单是否被网关改写）、回调配置缺失（是否按单号查库得到正确 `configId` 并显式传入）、查单一直 `PAYING`（商户号/证书与下单环境是否一致）、回调重复（判重由使用方按业务单号处理）。
