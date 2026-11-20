# 支付域接入指南

无状态渠道聚合层：统一方法名、出入参、结果、回调验签归一。落库、判重、金额校验、状态推进、下游通知由使用方完成。

> 受理态 ≠ 终态，最终态来自回调或主动查单；`bizOrderNo` 贯穿全程。完整契约见 [README](../README.md)。

## 一、两种引入形态

### 1. Spring Boot starter（推荐）

引入 `conflux-pay-spring-boot-starter`，starter 经 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动装配 `PaymentGateway` 单例 Bean。**内置渠道（mock / 微信 / 支付宝 / 云闪付 / PayPal / 天满）由 `builder().build()` 经 SPI 清单全量自动注册，用户零配置即可使用**；使用方自定义的 `ChannelProvider` Bean 按同编码覆盖内置渠道。

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>conflux-pay-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

配置见 [config-keys.md](config-keys.md)，最小可跑配置见 `conflux-pay-example/application.yml`。注入即可用：

```java
@Autowired private PaymentGateway paymentGateway;
```

### 2. 纯 jar 手动组装

无 Spring 环境用 `PaymentGateway.builder()` 装配，零 Spring 依赖。内置渠道由 `build()` 自动注册，无需逐个 `.channel()`：

```java
// 方式 A：直接用内置内存实现（配置来源为代码 / 使用方自管）
InMemoryChannelConfigProvider configs = new InMemoryChannelConfigProvider()
        .register(WechatParams.fromMap(attr)                 // 渠道强类型配置，公共字段一等化
                .setConfigId("demo").setChannelCode("WECHAT")
                .setNotifyUrl("https://your.host/pay/notify/wechat"));

// 方式 B：自行实现 ChannelConfigProvider（从数据库 / 配置中心读取，每次调用读取）
ChannelConfigProvider configs = /* 使用方实现的配置提供者 */;

PaymentGateway gateway = PaymentGateway.builder()
        .configProvider(configs)
        .build();                                            // 内置渠道在此自动注册
```

`builder()` 必须设置 `configProvider`；内置渠道无需声明。运行期可用 `gateway.registerChannel(provider)` 增补自定义渠道，`gateway.registeredChannels()` 查看已注册渠道。配置不限于配置文件——配置文件实现只在 Spring Boot starter（`conflux.pay.configs.<configId>.<channelCode>.*`）里提供。

> 配置模型：公共字段（`configId` / `channelCode` / `notifyUrl` / `sandbox`）为一等字段，渠道个性参数（如服务商模式 `subMchNo`、微信 `merchantId` 等）放 `attr`，由各渠道 `XxxParams.fromMap(attr)` 解析。JDK 8 环境用 `LinkedHashMap` 装配 `attr`，没有 `Map.of`。

## 二、核心调用

```java
// 1) 下单：method 决定路由渠道，bizOrderNo 由使用方生成（渠道商户单号）
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

**金额单位统一为分（`Long`）**。可退余额校验、判重、状态推进都是业务的事，SDK 不校验。

## 三、回调接入（必做其一：回调入口或定时查单）

SDK 只做验签 + 归一，业务链路自己接。验签仍在框架，但**配置定位权在使用方**：业务按渠道单号查自己的库得到 `configId`，显式传给 `parseCallback(configId, request)`；框架据此取配置验签，不再从报文隐式反查账户键。

```java
@PostMapping("/callback/{channelCode}")
public String callback(@PathVariable String channelCode,
                       HttpServletRequest req) {
    // 1) 使用方先从报文取渠道单号，查自己 DB 定位 configId
    String configId = /* 按 bizOrderNo / channelOrderNo 查库得到 */;

    CallbackEvent event = paymentGateway.parseCallback(configId,
            new ChannelCallbackRequest()
                    .setChannelCode(channelCode)
                    .setHeaders(/* 请求头 */)
                    .setQueryParams(/* query */)
                    .setFormParams(/* form */)
                    .setBody(/* 原始 body */));

    // 2) 使用方：定位订单 → 判重 → 校验金额 → 推进状态（SDK 不介入）

    return paymentGateway.callbackSuccessReply(channelCode); // 处理异常用 callbackFailureReply
}
```

回调 URL 无需携带任何账户定位参数；多渠道/多实例共用一个回调端点即可，靠业务单号查库区分。

### 天满渠道（chinaums）的回调说明

天满通知报文中带 `sign`，官方「通知验签规则」给出了基于通讯密钥的验签算法；但天满渠道采用更强的**回调触发主动查单确认**：回调处理器从报文取 `merOrderId` / `mid` 定位配置后，以 OPEN-BODY-SIG 双向认证调用 `/v1/netpay/query` 获取权威状态构造事件，不信任通知内容里的状态与金额。验签只能证明报文来源，查单才是渠道当前真实状态，故以查询接口为准——这也符合官方文档「以查询接口响应的订单状态更新订单状态」的推荐。通讯密钥与产品密钥（`appKey`）是否相同未经真实凭证验证，本 SDK 不臆造验签实现。

因此天满回调需：① 通知地址能携带 `mid`（或由使用方按报文里的商户号查库）以定位 `configId`；② 使用方可放心以返回的 `CallbackEvent` 推进状态，无需自行验签。应答报文：成功 `SUCCESS`、失败 `FAILED`（官方「支付结果通知」规则）。

## 四、接入自定义渠道（SPI）

新增渠道不用改内核，实现 `ChannelProvider` 及所需能力 Handler，再注册即可。

1. **实现 `ChannelProvider`**：声明 `channelCode()`、`supportedMethods()`；按需要提供 `paymentHandler()` / `refundHandler()` / `transferHandler()` / `profitShareHandler()` / `billHandler()` / `callbackHandler()`（均为 default 方法，只实现支持的能力）。
2. **实现能力 Handler**：如 `PaymentHandler.createPayment/create...`，返回值即归一结果；渠道 SDK/签名/报文封闭在渠道包内。
3. **注册支付方式**：在启动期用 `PayMethod.register("MY_METHOD", "mychannel")` 注册「方式编码 → 默认渠道」映射；使用方下单传 `method="MY_METHOD"` 即可路由到你的渠道。
4. **注册渠道**：
   - starter 场景：把你的 `ChannelProvider` 声明为 Spring Bean（自动装配按同编码覆盖内置渠道）；
   - 纯 jar 场景：`builder().channel(new MyChannelProvider())` 或 `gateway.registerChannel(...)`。
5. **配置键**：按 [config-keys.md](config-keys.md) 的「统一基础键 + `attr` 扩展键」两层模型，在 `conflux.pay.configs.<configId>.<渠道编码>` 下声明。

## 五、文档索引

- [config-keys.md](config-keys.md) — 渠道配置键对照表
- [integration-checklist.md](integration-checklist.md) — 真实渠道联调清单
- [invoice-platforms.md](invoice-platforms.md) — 发票平台接入说明
