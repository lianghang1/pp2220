# Conflux

可嵌入的无状态渠道聚合 SDK（**JDK 8+ / Spring Boot 2.7.x**），把各家渠道的**方法名、出入参、返回结果、回调验签**归一成统一 API。目前包含 **支付** 与 **数电发票** 两个能力域。

> **定位**：Conflux 不是独立运行的中间件，而是给成熟应用直接引入的 SDK。它只做一件事——统一渠道差异；**不持有台账、不做幂等/CAS、不维护状态机、不落库、不通知下游**。落库、判重、状态推进、下游通知、定时任务全部由使用方在自己的业务链里完成。

## 核心契约（先读这段）

- **SDK 无状态**：所有 `xxxResult` 只是渠道归一后的结果快照，SDK 不暂存任何单据；`Invoice` / `RedApply` / `AuthSession` 等票据载体随结果返回、随后续请求原样回传，由使用方保管。
- **受理 ≠ 终态**：下单/开票返回的 `state` 是渠道**受理态**；最终态来自**渠道回调**或**主动查单**。使用方必须实现回调入口或定时查单（至少其一）。
- **判重与金额校验在使用方**：回调事件只会验签 + 归一，业务单号定位、重复事件判重、金额比对、状态推进都是业务的事。
- **业务单号是唯一线索**：`bizOrderNo` / `bizRefundNo` 等由使用方生成并传入，作为渠道商户单号；SDK 不生成内部单号、不查重、不返回既有订单。
- **金额单位**：支付 = **分**（`Long`）；发票 = **元**（`BigDecimal`）。

## 模块构成

`conflux-pay`（core 内核 / channels 渠道实现 / spring-boot-starter / example）、`conflux-invoice`（同构四件套）、`conflux-common`（公共工具 / 异常 / HTTP 传输）。core 与 channels 零 Spring 依赖，starter 提供 Boot 自动装配。

---

## 能力域一：支付（conflux-pay）

- **统一支付 API**：下单 / 查单 / 关单、退款、转账、分账，一套出入参模型，全部返回归一结果快照
- **多渠道适配**：渠道按包隔离，渠道专属 SDK/签名/报文封闭在各自包内，新增渠道零改内核
- **零状态**：框架不建台账、不落库；业务单号由使用方传入，判重/金额校验/状态推进由使用方完成
- **双形态引入**：纯 jar 手动组装（`PaymentGateway.builder()`）/ Spring Boot starter 自动装配；配置来源不限配置文件
- **回调验签归一**：`parseCallback` 验签 + 归一为统一事件，应答报文由门面生成；下游通知由使用方自行处理

| 渠道 | 支付方式 | 退款 | 转账 | 分账 | 账单 | 回调验签 |
|---|---|---|---|---|---|---|
| wechat | JSAPI / 小程序 / Native / H5 / APP / 付款码，V2·V3 双协议 | ✓ | ✓ | ✓ | ✓ | V3 平台证书 / V2 MD5 |
| alipay | 扫码 / WAP / 电脑网站 / APP / JSAPI / 付款码 | ✓ | ✓ | ✓ | ✓ | RSA2 公钥 |
| unionpay | 银联二维码·付款码 / 转接微信·支付宝 | ✓ | — | — | — | 平台公钥 |
| paypal | Orders v2（批准→捕获） | ✓ | Payouts | — | — | Webhook 官方验签 |
| chinaums | 微信/支付宝/云闪付 × APP/H5/小程序/公众号 + C扫B主扫 | ✓ | — | — | — | 回调触发主动查单 |
| mock | 二维码模拟 | ✓ | ✓ | ✓ | — | 自定义头 |

**多账户回调路由**：同一渠道可配置多个账户，服务商模式用 `accountKey`（`channelAppId#subMchNo`）在回调 URL 上定位账户；装载期账户键冲突立即启动失败，不静默覆盖。

### 快速开始（支付）

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>conflux-pay-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```yaml
conflux.pay.apps.demo.mock:
  notifyUrl: http://127.0.0.1:8088/callback/mock
  attrs:
    appKey: demo-app-key
    payOutcome: paying
```

注入 `PaymentGateway` 即可调用。全部方法（下单/查单/关单/退款/转账/分账/账单/回调）与出入参字段见 [docs/api-reference.md](docs/api-reference.md)；配置键与联调清单见 [docs/config-keys.md](docs/config-keys.md)、[docs/integration-checklist.md](docs/integration-checklist.md)。

```java
// 下单：bizOrderNo 由使用方生成，作为渠道商户单号
PaymentResult pay = paymentGateway.createPayment(new PaymentRequest()
        .setAppId("demo").setBizOrderNo("ORDER_001")
        .setMethod(PayMethod.WECHAT_NATIVE.getCode())
        .setAmount(9900L).setSubject("订单支付"));
// state=PAYING → 取 pay.getPayPayload() 拉起支付；state=SUCCEEDED → 即时扣款完成
// 渠道单号 channelOrderNo 由使用方自己保存（SDK 不存）

// 主动查单（最终态也可来自回调）
PaymentResult snapshot = paymentGateway.queryPayment(new PaymentQueryRequest()
        .setAppId("demo").setBizOrderNo("ORDER_001")
        .setChannelCode(ChannelCode.WECHAT).setChannelOrderNo(channelOrderNo));

// 回调只验签 + 归一；判重/金额校验/状态推进由使用方完成
CallbackEvent event = paymentGateway.parseCallback(callbackRequest);
// ... 使用方按 event.getBizOrderNo() 定位订单，判重、校验金额、推进状态 ...
return paymentGateway.callbackSuccessReply(channelCode);
```

---

## 能力域二：数电发票（conflux-invoice）

- **统一发票 API**：蓝字开票、授权、红冲（两段 + 快捷）、查单、交付、版式文件，一套出入参模型
- **多通道择优**：按 `sellerTaxNo` + 通道分级（`ChannelTier`）在乐企直连 / 服务商 API / 网页 RPA 间择优路由
- **票据载体往返**：授权会话 `AuthSession`、红字确认单 `RedApply`、发票 `Invoice` 随结果返回、随后续请求回传，SDK 不暂存
- **异步与法定原件**：归一 `WAITING_AUTH` / `PROCESSING` 等挂起态；XML 是法定原件，无原件不置 `SUCCEEDED`
- **双形态引入**：纯 jar（`InvoiceGateway.builder()`）/ Spring Boot starter 自动装配

> **状态语义由渠道返回、由使用方推进**：SDK 只归一渠道状态，不维护状态机、不做迁移约束判非法。挂起态由使用方保管载体并在回调/查单后重新发起相应调用。渠道支持情况见下方「发票平台支持现状」。

### 快速开始（发票）

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>conflux-invoice-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
conflux.invoice:
  mock-enabled: true          # 默认注册 mock 渠道，零凭据启动
  apps:
    demo:
      mock:
        seller-tax-no: "91310000MOCK000001"
        notify-url: "http://127.0.0.1:8080/api/invoices/callback/mock?accountKey=demo-app-key%2391310000MOCK000001"
        tier: SERVICE_API
        attrs:
          appKey: demo-app-key
          appSecret: demo-secret
          issueOutcome: AUTH       # AUTH / ASYNC / SUCCESS / FAIL
          redConfirmOutcome: CONFIRMED
```

注入 `InvoiceGateway` 即可调用。全部方法（开票/授权/红冲/查单/交付/修复/文件/回调）与出入参字段见 [docs/api-reference.md](docs/api-reference.md)。票据载体随结果返回、随请求回传，由使用方保管。

```java
InvoiceResult r = invoiceGateway.issue(new InvoiceRequest()
        .setAppId("demo").setBizOrderNo("INV_001")
        .setSellerTaxNo("91310000MOCK000001").setBuyerTitle("购方企业")
        .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
        .setItems(items).setAmount(new BigDecimal("1000.00")));

if (r.getState() == InvoiceState.WAITING_AUTH) {
    // 引导用户完成短信/扫码授权，随后回传会话与发票载体
    InvoiceResult done = invoiceGateway.submitAuth(new AuthSubmission()
            .setAppId("demo").setSession(r.getAuthSession())
            .setInvoice(r.getInvoice()).setCaptcha("123456"));
    // done.getInvoice() 由使用方保存，红冲/交付要用
}

// 两段红冲：申请确认单 → 查确认 → 开红票（载体全程由使用方回传）
RedApply apply = invoiceGateway.applyRed(new RedApplyRequest()
        .setAppId("demo").setBizRedNo("RED_001").setOriginalInvoice(invoice));
RedApply confirmed = invoiceGateway.queryRedApply(new RedApplyQueryRequest()
        .setAppId("demo").setRedApply(apply));
if (confirmed.getState() == RedApplyState.CONFIRMED) {
    InvoiceResult red = invoiceGateway.issueRed(new RedIssueRequest()
            .setAppId("demo").setRedApply(confirmed).setOriginalInvoice(invoice));
}
// 也可用快捷红冲一次覆盖全流程：invoiceGateway.quickRed(...)，用 outcome 分流
```

`conflux-invoice-example` 提供可直接运行的 REST 宿主，默认 mock 渠道，零凭据即可跑通完整链路。

版式文件按需处理：`gateway.pageImages(pdfBytes, config)` 逐页转图供预览，`gateway.printAdaptedPdf(pdfBytes, paper, dpi)` 栅格化重组指定纸型 PDF（解决特殊字体直接打印丢字）；转换时机与参数由使用方决定，框架不自动转换。

---

## Spring Boot 与 Spring MVC 接入

**两种环境都完整支持**。core 与 channels 是纯 Java（Spring 只出现在测试范围）：

| 运行环境 | 接入方式 | 引入模块 | 配置来源 |
|---|---|---|---|
| Spring Boot | starter 自动装配（推荐） | `conflux-pay-spring-boot-starter` / `conflux-invoice-spring-boot-starter` | `application.yml` 或自实现配置源 |
| Spring MVC / 传统 Spring / 纯 Java | 纯 jar 手动组装 | `conflux-pay-core` + `conflux-pay-channels`（发票同理） | 任意来源：内存 / DB / 配置中心 / properties |

starter 依赖 `spring-boot-autoconfigure`，仅适用于 Spring Boot；MVC 项目请引入 core + channels，不要引入 starter。

> **"SPI"与"自动装配"不是一回事**：本项目的 SPI 指扩展点接口（`ChannelProvider` 自定义渠道、`ChannelConfigProvider` 自定义配置源），两个环境完全一致。Boot 的"自动装配"是 starter 经 `AutoConfiguration.imports` 让 Spring 自动发现配置类，只是省去手写 `@Bean`；传统 Spring MVC 用 `@Configuration` + `@Bean` 注册，纯 Java 用 `builder()` 手动 new。差别只在"谁来创建对象"，不在扩展能力。

Spring Boot 配置例（支付，发票见下节）：

```yaml
conflux.pay:
  apps:
    demo:
      wechat:
        notifyUrl: https://your.host/pay/callback/wechat
        sandbox: false
        # subMchNo: "1900000000"   # 服务商模式特约商户号（可选）
        attrs:                     # 渠道个性键，键名由各渠道定义
          merchantId: "1900000001"
          appId: "wx8888888888888888"
          apiV3Key: "your-32-bytes-apiV3-key-aaaaaaaaaaaa"
          merchantSerialNumber: "5157F09EFDC096DE15EBE81A47057A72..."
          merchantPrivateKey: "/data/cert/apiclient_key.pem"
```

天满渠道（银联商务，多渠道聚合）配置例：

```yaml
conflux.pay:
  apps:
    demo:
      chinaums:
        notifyUrl: https://your.host/pay/callback/chinaums   # 通知需能定位 mid（或用 ?accountKey=）
        sandbox: false                    # true 切 test-api-open.chinaums.com
        attrs:
          appId: "天满产品ID"
          appKey: "天满产品密钥"
          mid: "898340149000005"           # 商户号，同时作为回调配置反查键
          tid: "88880001"                  # 终端号
          subAppId: "wx8888888888888888"   # 微信渠道下单所需，非微信渠道可省
```

下单用 `method` 选方式：`CHINAUMS_WX_APP` / `CHINAUMS_WX_H5` / `CHINAUMS_WX_MINI` / `CHINAUMS_WX_JSAPI`，以及对应的 `ALI_*` / `UAC_*` 组合和 `CHINAUMS_QR`（C扫B）。回调采用主动查单确认，详见 [docs/pay-integration.md](docs/pay-integration.md)。

Spring MVC 纯 jar 装配例：

```java
@Bean
public PaymentGateway paymentGateway() {
    return PaymentGateway.builder()
            .channel(new WechatChannelProvider())
            .channel(new AlipayChannelProvider())
            // 内置内存配置；也可换成自实现 ChannelConfigProvider，从 DB/配置中心读
            .configProvider(new InMemoryChannelConfigProvider()
                    .register(new ChannelConfig()
                            .setAppId("demo").setChannelCode("WECHAT")
                            .setNotifyUrl("https://your.host/pay/callback/wechat")
                            .setParams(WechatParams.fromMap(Map.of(
                                    "merchantId", "1900000001",
                                    "appId", "wx8888888888888888",
                                    "apiV3Key", "your-32-bytes-apiV3-key-aaaaaaaaaaaa")))))
            .build();
}
```

发票域装配同理（`InvoiceGateway.builder()` + `InMemoryInvoiceConfigProvider`）。回调接入两种环境完全一致：暴露回调端点，调 `parseCallback` 验签归一后自行推进状态，示例见 [docs/pay-integration.md](docs/pay-integration.md)。

---

## 发票平台支持现状

框架不编造未核实的 URL 与字段：未核对的渠道默认不带接口路径，使用方按平台公开文档在 `attrs` 中配置路径与字段映射后即可启用。

| 渠道编码 | 平台 | 文档核对状态 | 说明 |
|---|---|---|---|
| `mock` | 内置模拟 | 不适用 | 全能力：开票 / 授权 / 异步 / XML 原件 / 两段红冲 / 回调 |
| `qixiangyun` | 企享云 | 蓝字开具已核对 | 查单/红字/授权/文件下载需按文档配置路径 |
| `baiwangjs` | 百旺金穗云 | 已核对（官方公开文档） | 专属适配器：蓝字/查单/修复 V2 优先、V1 可切；红字两段、登录认证、版式文件、交付重发、XML 原件 |
| `piaotong` | 票通云 | 已核对（官方公开文档） | 专属适配器：蓝字开具、查单、红字两段、登录认证、文件下载、加密回调 |
| `baiwang` | 百旺票税云 | 待核对 | 骨架已注册 |
| `kingdee` | 金蝶发票云 | 待核对 | 骨架已注册 |

> 各渠道均为协议层实现 + 单测，**尚未做真实平台联调**；字段级配置见 [docs/invoice-platforms.md](docs/invoice-platforms.md)。

---

## 设计约定

- **错误码**：1xxx 通用 / 2xxx 支付 / 3xxx 发票
- **账户键**：支付 `channelAppId#subMchNo`、发票 `channelAppId#sellerTaxNo`；回调 URL 带 `?accountKey=...`（`#` 转义为 `%23`），装载期冲突 fail-fast
- **渠道实现无状态**：运行态数据一律从请求/调用上下文取得
- **全栈 JDK 8**：不使用 JDK 9+ API，兼容老运行环境

## 构建与测试

```bash
mvn -q -DfailIfNoTests=false test
```

当前全量 **262 个用例全绿**。

## 文档索引

- [docs/api-reference.md](docs/api-reference.md) — **API 参考**：两个网关的全部方法、出入参字段、枚举取值
- [docs/pay-integration.md](docs/pay-integration.md) — 支付域接入指南（两种引入形态、核心调用、回调接入、自定义渠道）
- [docs/config-keys.md](docs/config-keys.md) — 支付渠道配置键对照表与服务商模式回调路由
- [docs/integration-checklist.md](docs/integration-checklist.md) — 真实渠道联调清单
- [docs/invoice-platforms.md](docs/invoice-platforms.md) — 发票平台核对清单与接入说明
