# 支付域接入指南

无状态渠道聚合层：统一方法名、出入参、结果、回调验签归一。落库、判重、金额校验、状态推进、下游通知由使用方完成。

> 受理态 ≠ 终态，最终态来自回调或主动查单；`bizOrderNo` 贯穿全程。完整契约见 [README](../README.md)。

## 一、两种引入形态

### 1. Spring Boot starter（推荐）

引入 `conflux-pay-spring-boot-starter`，starter 经 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动装配 `PaymentGateway` 单例 Bean，内置渠道（mock / 微信 / 支付宝 / 云闪付 / PayPal）按需注册为条件 Bean（`@ConditionalOnMissingBean`，使用方自定义同类型 Bean 即覆盖）。

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

无 Spring 环境用 `PaymentGateway.builder()` 装配，零 Spring 依赖：

```java
// 方式 A：直接用内置内存实现（配置来源为代码 / 使用方自管）
InMemoryChannelConfigProvider configs = new InMemoryChannelConfigProvider()
        .register(new ChannelConfig()
                .setAppId("demo").setChannelCode("WECHAT")
                .setNotifyUrl("https://your.host/pay/notify/wechat")
                .setParams(new WechatParams()));   // params 为渠道自定义参数对象

// 方式 B：自行实现 ChannelConfigProvider（从数据库 / 配置中心读取）
ChannelConfigProvider configs = /* 使用方实现的配置提供者 */;

PaymentGateway gateway = PaymentGateway.builder()
        .channel(new WechatChannelProvider())
        .channel(new AlipayChannelProvider())
        .configProvider(configs)
        .build();
```

`builder()` 必须设置 `configProvider`；渠道通过 `.channel(...)` 逐个注册，运行期还可用 `gateway.registerChannel(provider)` 增补，`gateway.registeredChannels()` 查看已注册渠道。

`InMemoryChannelConfigProvider` 注册时自动按 `ChannelAccountKey.of(params.channelAppId(), subMchNo)` 派生回调账户键（服务商模式为 `channelAppId#subMchNo`），账户键冲突注册即抛 `CONFIG_CONFLICT`；配置不限于配置文件——配置文件实现只在 Spring Boot starter（`conflux.pay.apps.<appId>.<channel>.*`）里提供。

## 二、核心调用

```java
// 1) 下单：method 决定路由渠道，bizOrderNo 由使用方生成（渠道商户单号）
PaymentResult pay = gateway.createPayment(new PaymentRequest()
        .setAppId("demo").setBizOrderNo("ORDER_001")
        .setMethod(PayMethod.WECHAT_NATIVE.getCode())
        .setAmount(9900L).setSubject("订单支付"));
// state=PAYING → pay.getPayPayload() 拉起支付；SUCCEEDED → 即时扣款已完成
// channelOrderNo 由使用方保存（SDK 不存）

// 2) 查单：最终态兜底（回调丢失时）
PaymentResult snapshot = gateway.queryPayment(new PaymentQueryRequest()
        .setAppId("demo").setBizOrderNo("ORDER_001")
        .setChannelCode(ChannelCode.WECHAT).setChannelOrderNo(channelOrderNo));

// 3) 退款 / 转账 / 分账：原单要素由使用方随请求提供
RefundResult refund = gateway.createRefund(new RefundRequest()
        .setAppId("demo").setBizRefundNo("REFUND_001")
        .setChannelCode(ChannelCode.WECHAT).setBizOrderNo("ORDER_001")
        .setTotalAmount(9900L).setAmount(9900L));
```

**金额单位统一为分（`Long`）**。可退余额校验、判重、状态推进都是业务的事，SDK 不校验。

## 三、回调接入（必做其一：回调入口或定时查单）

SDK 只做验签 + 归一，业务链路自己接：

```java
@PostMapping("/callback/{channelCode}")
public String callback(@PathVariable String channelCode,
                       HttpServletRequest req) {
    CallbackEvent event = paymentGateway.parseCallback(new ChannelCallbackRequest()
            .setChannelCode(channelCode)
            .setHeaders(/* 请求头 */)
            .setQueryParams(/* query */)
            .setFormParams(/* form */)
            .setBody(/* 原始 body */));

    // 使用方：定位订单 → 判重 → 校验金额 → 推进状态（SDK 不介入）

    return paymentGateway.callbackSuccessReply(channelCode); // 处理异常用 callbackFailureReply
}
```

**多账户回调路由**：同一渠道多账户时，回调 URL 必须带 `?accountKey=<key>`（服务商模式 `appKey#subMchNo`，`#` 转义为 `%23`），SDK 按账户键反查配置验签；键冲突在装载期抛 `CONFIG_CONFLICT` 使启动失败。

## 四、接入自定义渠道（SPI）

新增渠道不用改内核，实现 `ChannelProvider` 及所需能力 Handler，再注册即可。

1. **实现 `ChannelProvider`**：声明 `channelCode()`、`supportedMethods()`；按需要提供 `paymentHandler()` / `refundHandler()` / `transferHandler()` / `profitShareHandler()` / `billHandler()` / `callbackHandler()`（均为 default 方法，只实现支持的能力）。
2. **实现能力 Handler**：如 `PaymentHandler.createPayment/create...`，返回值即归一结果；渠道 SDK/签名/报文封闭在渠道包内。
3. **注册支付方式**：在启动期用 `PayMethod.register("MY_METHOD", "mychannel")` 注册「方式编码 → 默认渠道」映射；使用方下单传 `method="MY_METHOD"` 即可路由到你的渠道。
4. **注册渠道**：
   - starter 场景：把你的 `ChannelProvider` 声明为 Spring Bean（自动装配的 `@ConditionalOnMissingBean` 不会冲突）；
   - 纯 jar 场景：`builder().channel(new MyChannelProvider())` 或 `gateway.registerChannel(...)`。
5. **配置键**：按 [config-keys.md](config-keys.md) 的「统一基础键 + `attrs` 扩展键」两层模型，在 `conflux.pay.apps.<appId>.<渠道>` 下声明。

## 五、文档索引

- [config-keys.md](config-keys.md) — 渠道配置键对照表与账户键路由
- [integration-checklist.md](integration-checklist.md) — 真实渠道联调清单
- [invoice-platforms.md](invoice-platforms.md) — 发票平台接入说明
