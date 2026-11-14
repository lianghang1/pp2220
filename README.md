# pay-gateway

可嵌入的聚合支付网关框架（JDK 8+）。

## 特性

- **统一支付 API**：支付、查询、关闭、退款、转账、分账，一套出入参模型
- **多渠道适配**：渠道实现集中单模块按包隔离，渠道专属 SDK/签名/报文封闭在各自包内；新增渠道零改内核
- **零表结构**：框架不建表、不绑存储，持久化通过 Store SPI 交给使用方实现（订单、通知、对账差错等记录全部自定义落库）
- **双形态引入**：任意 Java 应用直接引 core jar 手动组装；Spring Boot 应用引 starter 自动装配
- **异步通知**：渠道回调验签解析 → 统一状态机迁移 → 事件发布 → 下游 HTTP 通知（分级退避重试 + 通知留痕）
- **对账**：渠道账单与本地订单比对，差错分类留痕、渠道成功而本地未成功的订单自动修正

## 模块

| 模块 | 说明 |
|---|---|
| pay-gateway-core | 渠道无关内核：模型、状态机、SPI、编排引擎，零 Spring 依赖 |
| pay-gateway-channels | 全部渠道实现，按包隔离（mock / wechat / alipay / unionpay / paypal） |
| pay-gateway-spring-boot-starter | Spring Boot 2.x 自动装配，`pay-gateway.*` 配置绑定 |
| pay-gateway-example | 演示宿主：starter 接入 + 使用方自行实现 Store + 回调入口 |

渠道能力矩阵：

| 渠道 | 支付方式 | 退款 | 转账 | 分账 | 账单 | 回调验签 |
|---|---|---|---|---|---|---|
| wechat | JSAPI(公众号) / LITE(小程序) / Native(WEB扫码) / H5 / APP / BAR(付款码)，V2/V3 双协议 | ✓ | 商家转账(V3)+企业付款(V2) | ✓ | ✓ | V3 平台证书验签 / V2 MD5 |
| alipay | 扫码 / WAP / 电脑网站 / APP / JSAPI / 付款码 | ✓（同步） | 单笔转账 | ✓（结算规则） | ✓ | RSA2 公钥 |
| unionpay | 银联二维码 / 银联付款码 / 转接微信 JSAPI·付款码 / 转接支付宝 JSAPI·付款码 | ✓ | — | — | — | 平台公钥 |
| paypal | Orders v2（批准→捕获） | ✓（捕获单） | Payouts | — | — | Webhook 官方验签接口 |
| mock | 二维码模拟 | ✓ | ✓ | ✓ | — | 自定义头 |

## 联调

真实账号（沙箱）到位后按 [docs/integration-checklist.md](docs/integration-checklist.md) 与 [docs/config-keys.md](docs/config-keys.md) 逐项联调；
example 内置沙箱配置模板（application-sandbox.yml）。

## 快速开始（Spring Boot）

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>pay-gateway-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 渠道专属 SDK 按需引入（可选依赖，缺依赖的渠道不激活） -->
<dependency>
    <groupId>com.github.wechatpay-apiv3</groupId>
    <artifactId>wechatpay-java</artifactId>
</dependency>
```

### 2. 配置

```yaml
pay-gateway:
  notify:
    retry-seconds: [15, 15, 30, 180, 600, 1200]
    secret: 下游通知签名密钥（可选，配置后通知携带 HMAC 头）
  apps:
    app-001:
      channels:
        wechat:
          merchantId: 商户号
          merchantSerialNumber: 商户证书序列号
          merchantPrivateKey: "PEM 私钥文本（\n 转义换行）"
          apiV3Key: APIv3密钥
          appId: 微信AppID
          wxApiVersion: v3            # v2/v3 双协议，缺省 v3
          apiV2Key: V2商户支付密钥    # wxApiVersion=v2 或付款码/企业付款时必填
          notifyUrl: https://你的域名/callback/wechat
        alipay:
          appId: 支付宝AppID
          privateKey: 应用私钥
          alipayPublicKey: 支付宝公钥
          notifyUrl: https://你的域名/callback/alipay
        unionpay:
          merchantId: 商户号
          p12CertPath: /cert/unionpay.p12
          p12CertPassword: 证书密码
          publicKey: 平台公钥
          notifyUrl: https://你的域名/callback/unionpay
        paypal:
          sandbox: true
          clientId: ClientID
          clientSecret: ClientSecret
          webhookId: WebhookID
        mock:
          appKey: mock-app-key
```

### 3. 实现 Store（框架不建表，按你的习惯落库）

以支付单为例，实现并注册为 Bean（其余退款/转账/分账/通知/差错存储按需）：

```java
@Component
public class MyPaymentStore implements PaymentStore {
    // save / update / findById / findByBizOrderNo（幂等键）
    // findByChannelOrderNo / findPayingBefore（补单） / findByChannelAndDate（对账）
}
```

建议：`appId + 业务单号` 建唯一索引（幂等）、渠道单号建普通索引（回调/补单定位）。完整示例见 `pay-gateway-example`（JPA/H2 实现）。

### 4. 注入网关并下单

```java
PaymentResult result = paymentGateway.createPayment(new PaymentRequest()
        .setAppId("app-001")
        .setBizOrderNo("订单号")
        .setMethod("WECHAT_NATIVE")   // 或 ALI_QR / UNION_QR / PAYPAL_SALE ...
        .setAmount(9900L)             // 分
        .setSubject("商品标题")
        .setNotifyUrl("http://你的业务/notify"));  // 支付成功后网关回调你的业务
// result.getPayPayload(): QRCODE 码值 / REDIRECT 跳转链接 / JSAPI 唤起参数
```

### 5. 暴露渠道回调入口（使用方自写 Controller）

```java
@PostMapping("/callback/{channelCode}")
public ResponseEntity<String> callback(@PathVariable String channelCode,
        @RequestHeader Map<String, String> headers,
        @RequestParam Map<String, String> queryParams,
        @RequestBody(required = false) String body, HttpServletRequest request) {
    try {
        ChannelCallbackReply reply = paymentGateway.handleCallback(new ChannelCallbackRequest()
                .setChannelCode(channelCode).setHeaders(headers)
                .setQueryParams(queryParams).setFormParams(request.getParameterMap()...)
                .setBody(body));
        return ResponseEntity.status(reply.getStatusCode()).body(reply.getBody());
    } catch (GatewayException e) {
        return ResponseEntity.ok(paymentGateway.callbackFailureReply(channelCode)); // 触发渠道重推
    }
}
```

约定：微信回调地址带 `?appId=...`、云闪付带 `?merId=...`、PayPal 带 `?clientId=...` 查询参数用于配置反查。

### 6. 兜底定时任务（通知重试 + 补单）

```java
@Scheduled(fixedDelay = 10000)
public void retryNotifies() { paymentGateway.retryPendingNotifies(100); }

@Scheduled(fixedDelay = 30000)
public void reissue() {
    for (Payment p : paymentStore.findPayingBefore(LocalDateTime.now().minusMinutes(2), 50)) {
        paymentGateway.queryPayment(new PaymentQueryRequest().setPaymentId(p.getPaymentId()));
    }
}
```

## 纯 jar 接入（无 Spring）

```java
PaymentGateway gateway = PaymentGateway.builder()
        .channel(new MockChannelProvider())          // 或自定义渠道
        .configProvider((appId, channel) -> myConfig) // 渠道配置提供者
        .paymentStore(myPaymentStore)
        .refundStore(myRefundStore)
        .notifyLogStore(myNotifyLogStore)
        .listener(event -> log.info("订单事件: {}", event.getType()))
        .build();
```

## 订单状态机

`CREATED → PAYING → SUCCEEDED / FAILED / CLOSED`；`SUCCEEDED → PART_REFUNDED → REFUNDED`。
渠道回调、查单补单、对账修正共用同一状态机，重复通知按幂等语义处理。

## 渠道差异说明

- **金额**：框架与 Store 统一用「分」（Long）；微信/云闪付原生接口分，支付宝/PayPal 由渠道包完成分↔元换算
- **回调配置反查**：微信（appId）/ 支付宝（app_id，通知体内）/ 云闪付（merId）/ PayPal（clientId）——均通过回调地址查询参数或报文字段定位；服务商模式统一用 `accountKey`（`channelAppId#subMchNo`），账户键冲突在启动期抛 `CONFIG_CONFLICT`
- **服务商模式**：微信（退款/转账/分账支持特约商户号）、云闪付（下单/退款支持 subMchNo）；微信/支付宝支付下单的服务商模式待扩展
- **协议版本**：微信经 WxJava（binarywang 二次封装）接入，V2/V3 双协议按 `wxApiVersion` 路由（付款码/企业付款走 V2）；支付宝官方 SDK 单协议；云闪付/PayPal 为 okhttp 直连
- **云闪付/PayPal 真实联调** 需提供对应商户凭据后验证

## 扩展新渠道

零改内核，三步接入（示例：新增某三方/银行间联通道）：

1. **实现 ChannelProvider**（可新建包或直接在使用方工程内实现）：
```java
public class MyChannelProvider implements ChannelProvider {
    public String channelCode() { return "my-channel"; }
    public Set<String> supportedMethods() { return Set.of("MY_QR"); }
    public PaymentHandler paymentHandler() { return myPaymentHandler; }  // 能力按需实现
    public ChannelParams parseParams(Map<String, String> properties) { return MyParams.fromMap(properties); }
}
```
2. **实现 Store 之外的 SPI**：渠道参数类型实现 `ChannelParams`（含 `channelAppId()` 供回调反查）；回调解析实现 `ChannelCallbackHandler`（验签 + 归一化 CallbackEvent + 应答报文），用 `ChannelAccountKey.resolve(raw, legacyId)` 取账户键并调用 `configLookup.byAccountKey(...)`；自定义 `ChannelConfigProvider` 实现 `getByAccountKey(channelCode, accountKey)`，账户键重复时抛 `CONFIG_CONFLICT`
3. **注册**：starter 场景注册为 Bean（`@ConditionalOnClass` 控制激活）；纯 jar 场景 `gateway.channel(provider)`

渠道包只做「统一模型 ↔ 渠道协议」的翻译；状态迁移、幂等、通知、对账全部由内核完成。

## 对账

```java
ReconReport report = paymentGateway.reconcile("wechat", "app-001", LocalDate.of(2026, 11, 14));
// 差错类型：CHANNEL_MISSING（本地有渠道无）/ LOCAL_MISSING / AMOUNT_MISMATCH / STATE_MISMATCH
// 渠道成功而本地未成功：自动修正本地状态并触发事件与下游通知；其余差错留痕待人工处理
```

## 注意事项

- 渠道 SDK 依赖为 optional，使用方按渠道引入对应坐标（wechatpay-java / alipay-sdk-java；云闪付与 PayPal 为 okhttp 直连无额外 SDK）
- 下游通知体在入队时固化，重试投递同一内容；接收方按 `orderId` 幂等处理，配置密钥后以 `X-Gateway-Signature`（HMAC-SHA256）校验防篡改
- 收款账号等敏感信息落日志请经 `MaskUtils` 打码
- 真实渠道联调需各渠道商户凭据（微信商户证书、支付宝公钥、银联 P12、PayPal 应用凭据）
