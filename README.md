# Conflux

可嵌入的多能力域渠道聚合 SDK（**JDK 8+ / Spring Boot 2.7.x**），目前包含 **支付** 与 **数电发票** 两个能力域。

> **定位**：Conflux 不是独立运行的中间件，而是给成熟应用直接引入的无状态 SDK。它只做一件事——把各家渠道的**方法名、出入参、返回结果、回调验签**归一成统一模型；**不持有台账、不做幂等/CAS、不维护状态机、不落库、不通知下游**。落库、判重、状态推进、下游通知、定时任务全部由使用方在自己的业务链里完成。

## 核心契约（先读这段）

- **SDK 无状态**：所有 `xxxResult` 只是渠道归一后的结果快照，SDK 不暂存任何单据；`Invoice` / `RedApply` / `AuthSession` 等票据载体随结果返回、随后续请求原样回传，由使用方保管。
- **受理 ≠ 终态**：下单/开票返回的 `state` 是渠道**受理态**；最终态来自**渠道回调**或**主动查单**。使用方必须实现回调入口或定时查单（至少其一）。
- **判重与金额校验在使用方**：回调事件只会验签 + 归一，业务单号定位、重复事件判重、金额比对、状态推进都是业务的事。
- **业务单号是唯一线索**：`bizOrderNo` / `bizRefundNo` 等由使用方生成并传入，作为渠道商户单号；SDK 不生成内部单号、不查重、不返回既有订单。
- **金额单位**：支付 = **分**（`Long`）；发票 = **元**（`BigDecimal`）。

## 工程结构

根 `pom.xml`（`conflux-parent`）是**纯聚合器**：不发布、不作为任何模块的 parent。每个能力域有独立父 pom 与独立版本号，互不影响发版节奏。三方依赖版本由 `conflux-dependencies` BOM **全量单源管理**：common / pay / invoice 三域以 `import` scope 引入、依赖声明一律不写 version——收录标准是「第三方 jar 即进 BOM」（不论当前被几个域使用，避免单域/跨域两段式维护），Spring Boot / OkHttp / Guava / PDFBox / 微信 / 支付宝升版只改 BOM 一处。内部构件版本（`conflux-common` 及各域自产模块）仍留各自域 pom。唯一例外是 `spring-boot-maven-plugin` 插件版本（不受 dependencyManagement 管理），升级 Spring Boot 时需与 BOM 的 `spring-boot.version` 同步。

```
conflux-parent (聚合器, 1.0.0)
├── conflux-dependencies    1.0.0          共享依赖版本 BOM（packaging=pom，single source of truth）
├── conflux-common          1.0.0          公共基座（工具 / 异常骨架 / HTTP 传输层）
├── conflux-pay             1.0.0-SNAPSHOT 支付能力域（4 个子模块）
│   ├── conflux-pay-core                 内核：模型、渠道 SPI、编排引擎，零 Spring 依赖
│   ├── conflux-pay-channels             渠道实现（mock / wechat / alipay / unionpay / paypal）
│   ├── conflux-pay-spring-boot-starter  Spring Boot 2.x 自动装配
│   └── conflux-pay-example              演示宿主
└── conflux-invoice         0.1.0-SNAPSHOT 发票能力域（4 个子模块）
    ├── conflux-invoice-core              发票内核（模型、渠道 SPI、编排引擎，零 Spring 依赖）
    ├── conflux-invoice-channels          渠道实现（mock + 企享云/百旺金穗云/百旺票税云/票通云/金蝶）
    ├── conflux-invoice-spring-boot-starter
    └── conflux-invoice-example
```

**命名约定**：产品名 `conflux` 永远作为第一段。

| 能力域 | Java 包 | 配置前缀 | 错误码段 |
|---|---|---|---|
| 公共基座 | `io.github.lianghang1.conflux.common.*` | — | 1xxx `CommonErrorCode` |
| 支付 | `io.github.lianghang1.conflux.pay.*` | `conflux.pay.*` | 2xxx `GatewayErrorCode` |
| 发票 | `io.github.lianghang1.conflux.invoice.*` | `conflux.invoice.*` | 3xxx `InvoiceErrorCode` |

公共基座只持 `ErrorCode` 契约与 1xxx 通用码；`JsonUtils` 只抛 JDK 标准 `IllegalArgumentException`，不依赖任何业务异常。OkHttp 只出现在 `common` 的 `HttpTransport`，pay 与 invoice 渠道一律经该传输层发请求，不直接依赖 OkHttp。

`invoice/support/PdfImageConverter` 提供发票 PDF 的图片化与栅格化能力（打印/展示防丢字场景：特殊字体 PDF 直接打印易丢字，栅格化后文字固化为像素）：三种模式——按原始页面尺寸逐页转图（PNG/JPEG，携带 DPI 物理元数据）、强制统一纸型（A4/A5/A3/B5/LETTER，内容等比缩放不裁切不变形，支持内容区域压缩与方向自动）、栅格化为单一 PDF。基于 PDFBox 2.x，由 conflux-invoice-core 依赖（版本经 `conflux-dependencies` BOM 管理，其传递的 commons-logging 已排除并经 jcl-over-slf4j 桥接到 slf4j）；服务端运行需 `java.awt.headless=true`（现代 JVM 默认已开启）。门面按需开放：`gateway.pageImages(pdfBytes, config)` 逐页转图、`gateway.printAdaptedPdf(pdfBytes, paper, dpi)` 栅格化重组 A4 PDF，转换时机与参数由使用方决定，框架不自动转换。

---

## 能力域一：支付（conflux-pay）

- **统一支付 API**：下单 / 查单 / 关单、退款、转账、分账，一套出入参模型，全部返回归一结果快照
- **多渠道适配**：渠道按包隔离，渠道专属 SDK/签名/报文封闭在各自包内，新增渠道零改内核
- **零状态**：框架不建台账、不落库；业务单号由使用方传入，判重/金额校验/状态推进由使用方完成
- **双形态引入**：纯 jar 手动组装（`PaymentGateway.builder()`）/ Spring Boot starter 自动装配
- **回调验签归一**：`parseCallback` 验签 + 归一为统一事件，应答报文由门面生成；下游通知由使用方自行处理

| 渠道 | 支付方式 | 退款 | 转账 | 分账 | 账单 | 回调验签 |
|---|---|---|---|---|---|---|
| wechat | JSAPI / 小程序 / Native / H5 / APP / 付款码，V2·V3 双协议 | ✓ | ✓ | ✓ | ✓ | V3 平台证书 / V2 MD5 |
| alipay | 扫码 / WAP / 电脑网站 / APP / JSAPI / 付款码 | ✓ | ✓ | ✓ | ✓ | RSA2 公钥 |
| unionpay | 银联二维码·付款码 / 转接微信·支付宝 | ✓ | — | — | — | 平台公钥 |
| paypal | Orders v2（批准→捕获） | ✓ | Payouts | — | — | Webhook 官方验签 |
| mock | 二维码模拟 | ✓ | ✓ | ✓ | — | 自定义头 |

**多账户回调路由**：同一渠道可配置多个账户。服务商模式用 `accountKey`（`channelAppId#subMchNo`）在回调 URL 上定位账户；配置装载期用 `putIfAbsent` 建唯一反查索引，键冲突立即抛 `CONFIG_CONFLICT` 使启动失败，绝不静默覆盖。详见 [docs/config-keys.md](docs/config-keys.md)。

### 快速开始（支付）

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>conflux-pay-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```yaml
conflux.pay.apps.demo.channels.mock:
  notifyUrl: http://127.0.0.1:8088/callback/mock
  attrs:
    appKey: demo-app-key
    payOutcome: paying
```

注入 `PaymentGateway` 即可调用；**无需实现任何 Store Bean**。完整配置键、联调清单与回调示例见 [docs/config-keys.md](docs/config-keys.md)、[docs/integration-checklist.md](docs/integration-checklist.md)。

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

参考支付域的 Ports & Adapters / Strategy 模式，把 `百旺 / 票通 / 航信 / 金蝶 / 企享云` 等服务商类比支付里的 `微信 / 支付宝`。但发票有支付没有的语义，因此内核不是支付模型的简单复制：

| 差异 | 支付 | 数电发票 | Conflux 的建模 |
|---|---|---|---|
| 企业准入 | 商户号即可 | 税号需准入、税局备案 | `sellerTaxNo` 作为配置维度与路由键 |
| 通道分级 | 无 | 乐企直连 / 服务商 API / 电局网页 RPA 并存 | `ChannelTier` + 择优路由 |
| 人工授权 | 无 | 扫脸 / 短信 / 扫码，登录态会过期 | `WAITING_AUTH` + `AuthSession` 载体往返 |
| 异步长耗时 | 少见 | 开票 40s+ 常转异步 | `PROCESSING` + 查单/回调推进 |
| 法定原件 | 无 | XML 是法定原件 | 无 XML 原件**拒绝**置为 `SUCCEEDED` |
| 红冲 | 退款一次调用 | 红字确认单需对方确认，两段长事务 | `RedApply` 载体 + 两段流程 |

> **状态语义由渠道返回、由使用方推进**：SDK 只归一渠道状态（`WAITING_AUTH` / `PROCESSING` / `SUCCEEDED` / 红冲状态等），不维护状态机、不做迁移约束判非法。挂起态（如 `WAITING_AUTH`、红字待确认）由使用方保管载体并在回调/查单后重新发起相应调用。

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
      channels:
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

注入 `InvoiceGateway` 即可调用；**无需注册任何 Store Bean**。票据载体随结果返回、随请求回传，由使用方保管。

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

`conflux-invoice-example` 提供可直接运行的 REST 宿主（`/api/invoices` 系列端点），默认 mock 渠道，零凭据即可跑通完整链路。

**辅助能力为渠道可选能力**：`InvoiceDeliverer` / `InvoiceRepairer` 与 `InvoiceRedHandler` 的可选方法未实现的渠道由注册表统一抛 `CHANNEL_CAPABILITY_NOT_SUPPORTED`，不影响 `mock` / 企享云等渠道；同一能力的 V1/V2 或同步/异步模式走 `attrs` 切换，不为版本单开 SPI 方法。

> 百旺金穗云按语雀《3.接口定义》逐条核对后，已实现 14 个 A/B 档接口（详见 [docs/invoice-platforms.md](docs/invoice-platforms.md) 第 3.5 节）；商品管理、机动车/二手车、乐企跨区、订单码、打印及**作废类**接口按「通用渠道平台 + 数电票只有红冲没有作废」的口径不纳入骨架。

---

## 发票平台接入现状（如实标注）

第一版先做「完全公开、有 API 文档」的平台。**框架不编造未经核实的 URL 与字段**：`RestChannelSpec.documentVerified` 标记文档核对状态，未核对的渠道默认不带任何接口路径、不暴露「假装能用」的能力，使用方按平台最新公开文档在 `attrs` 中配置路径与字段映射后即可启用。

**百旺金穗云已按用户指定的语雀官方公开文档完成专属适配器**：协议自带已核对的默认路径、网关签名与回调分流，不再走「先配路径再启用」的骨架流程，详见 [docs/invoice-platforms.md](docs/invoice-platforms.md) 第 3 节。

| 渠道编码 | 平台 | 通道级别 | 文档核对状态 | 已实现 |
|---|---|---|---|---|
| `mock` | 内置模拟 | SERVICE_API | 不适用 | 全能力：开票 / 授权 / 异步 / XML 原件 / 两段红冲 / 回调 |
| `qixiangyun` | 企享云 | SERVICE_API | **蓝字开具接口已核对** | `POST /v2/invoice/qdfp/fpkjZzs`，字段 `action/async/nsrsbh/ly_ddbh/wjgs/fplxdm/xmmx/jshj/hjse/data.xsf_yhmc`；响应 `reqId/code/success/message/data.fphm`，`code=2001` 转异步。查单/红字/授权/文件下载需按文档配置路径 |
| `baiwangjs` | 百旺金穗云 | SERVICE_API | **已核对（语雀官方文档）** | 专属适配器 `BaiwangJsChannelProvider`：Base64 报文 + HMAC 网关签名；蓝字开具 / 查单 / 发票修复 **V2 优先、V1 保留可切换**；红字两段、登录 / 实人认证、版式文件查询、交付重发、可冲销校验、`hzcx` 回调分流、XML 法定原件。协议层/编排层单测覆盖 |
| `piaotong` | 票通云 | SERVICE_API | **已核对（接口文档 3.2.0 + 生产实现交叉验证）** | 专属适配器 `PiaoTongChannelProvider`：明文信封 + 3DES 业务报文 + RSA 字典序签名 + 响应验签解密；蓝字开具、查单、红字两段、电局登录认证、文件下载（XML/PDF/OFD）、加密推送回调、交付重发等。协议层单测覆盖 |
| `baiwang` | 百旺票税云 | SERVICE_API | **待核对** | 骨架已注册；待按公开文档核对路径、票种编码与签名规则 |
| `kingdee` | 金蝶发票云 | SERVICE_API | **待核对** | 骨架已注册；待按公开文档核对路径、票种编码与签名规则 |

百旺金穗云启用示例（默认 V2，切 V1 只需改 `apiVersion`）：

```yaml
conflux.invoice.apps.demo.channels.baiwangjs:
  seller-tax-no: "91310000XXXXXXXXXX"
  notify-url: "http://host/api/invoices/callback/baiwangjs?accountKey=<GATEWAY_KEY>%2391310000XXXXXXXXXX"
  tier: SERVICE_API
  priority: 20
  enabled: true
  attrs:
    gatewayKey: "<网关KEY>"        # HMAC 签名 username，账户键第一段
    gatewaySecret: "<网关SECRET>"  # HmacSHA256 密钥
    qyId: "<企业唯一标识>"
    sellerTaxNo: "91310000XXXXXXXXXX"
    apiVersion: "V2"               # 改 V1：开票/查单整体切到 V1 路径与合并字段报文
```

> 通用 REST 适配器的签名默认是 HMAC-SHA256 占位，各平台规则不同，正式接入前须按文档覆写 `buildHeaders`；百旺金穗云已实现 `Base64(HmacSHA256(sign_str, SECRET))` 网关签名（`BaiwangJsSigner`），无需覆写。
> **所有渠道目前均为协议层实现 + 单测，尚未做真实平台联调。**

---

## 设计约定

- **错误码三段分层**：1xxx 通用 / 2xxx 支付 / 3xxx 发票，common 只持契约与通用段
- **账户键**：支付 `channelAppId#subMchNo`、发票 `channelAppId#sellerTaxNo`；回调 URL 带 `?accountKey=...`（`#` 需转义为 `%23`），装载期冲突 fail-fast
- **渠道实现无状态**：运行态数据一律从请求/调用上下文取得，Provider 上不保存单据状态
- **传输层边界**：`HttpTransport` 只返回原始 `HttpResponse(statusCode, body, headers)`，非 2xx 不抛异常；业务判定、JSON/XML 解析、错误码翻译全部在渠道层
- **全栈 JDK 8**：不使用 `var` / `List.of` / `String.isBlank` 等 JDK 9+ API

## 构建与测试

```bash
mvn -q -DfailIfNoTests=false test
```

当前全量 **258 个用例全绿**（common 17、pay 64、invoice 177；按模块 = common 17 / pay-core 25 / pay-channels 31 / pay-starter 8 / inv-core 37 / inv-channels 130 / inv-starter 9 / inv-example 1）。支付侧覆盖参数/状态映射、编排流程（下单/退款/转账/分账/回调）、渠道能力声明、starter 装配；发票侧覆盖账户键、渠道注册表、错误码分段、蓝字开票、授权往返、异步查单、XML 法定原件强约束、红冲两段与快捷红冲、回调归一、版式文件固化与转图、starter 装配与 example 端到端；其中百旺金穗云、票通云各有专属适配器协议层/编排层单测覆盖网关签名、报文编解码、V1/V2 差异、回调分流与状态归一。

## 文档索引

- [docs/pay-integration.md](docs/pay-integration.md) — 支付域接入指南（两种引入形态、核心调用、回调接入、自定义渠道 SPI）
- [docs/config-keys.md](docs/config-keys.md) — 支付渠道配置键对照表与服务商模式回调路由
- [docs/integration-checklist.md](docs/integration-checklist.md) — 真实渠道联调清单
- [docs/invoice-platforms.md](docs/invoice-platforms.md) — 发票平台文档核对清单与接入说明（第 3 节为百旺金穗云专属适配器的协议、V2/V1 路径、签名、回调分流与配置全表）
