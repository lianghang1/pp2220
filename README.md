# Conflux

可嵌入的多能力域聚合接入框架（**JDK 8+ / Spring Boot 2.7.x**），目前包含 **支付** 与 **数电发票** 两个能力域。

> **定位**：Conflux 不是独立运行的中间件，而是给成熟应用直接引入的 SDK。框架只负责「统一模型 ↔ 渠道协议」的翻译、状态机编排、回调路由与并发契约；持久化、下游通知接收、定时任务由使用方实现或注册为 Bean。

## 工程结构

根 `pom.xml`（`conflux-parent`）是**纯聚合器**：不发布、不作为任何模块的 parent。每个能力域有独立父 pom 与独立版本号，互不影响发版节奏。

```
conflux-parent (聚合器, 1.0.0)
├── conflux-common          1.0.0          公共基座（工具 / 异常骨架 / HTTP 传输层）
├── conflux-pay             1.0.0-SNAPSHOT 支付能力域（4 个子模块）
│   ├── conflux-pay-core                 内核：模型、状态机、SPI、编排引擎，零 Spring 依赖
│   ├── conflux-pay-channels             渠道实现（mock / wechat / alipay / unionpay / paypal）
│   ├── conflux-pay-spring-boot-starter  Spring Boot 2.x 自动装配
│   └── conflux-pay-example              演示宿主
└── conflux-invoice         0.1.0-SNAPSHOT 发票能力域（4 个子模块）
    ├── conflux-invoice-core              发票内核（可挂起状态机 / 渠道 SPI / Store 契约）
    ├── conflux-invoice-channels          渠道实现（mock + 4 家服务商 + 百旺金穗云/票通云专属适配器）
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

`common/util/PdfImageConverter` 提供发票 PDF 的图片化与栅格化能力（打印/展示防丢字场景：特殊字体 PDF 直接打印易丢字，栅格化后文字固化为像素）：三种模式——按原始页面尺寸逐页转图（PNG/JPEG，携带 DPI 物理元数据）、强制统一纸型（A4/A5/A3/B5/LETTER，内容等比缩放不裁切不变形，支持内容区域压缩与方向自动）、栅格化为单一 PDF（接口"一个文件进出"无需改动）。基于 PDFBox 2.x 且依赖声明为 `optional`，仅使用该能力时需自行引入 `org.apache.pdfbox:pdfbox`；服务端运行需 `java.awt.headless=true`（现代 JVM 默认已开启）。发票门面已把该能力按需开放：`gateway.pageImages(invoiceId, config)` 逐页转图、`gateway.printAdaptedPdf(invoiceId, paper, dpi)` 栅格化重组 A4 PDF，转换时机与参数由使用方决定，框架不自动转换；pdfbox 缺失时抛 `FILE_PRINT_UNSUPPORTED` 而非 `NoClassDefFoundError`。

---

## 能力域一：支付（conflux-pay）

- **统一支付 API**：支付、查询、关闭、退款、转账、分账，一套出入参模型
- **多渠道适配**：渠道按包隔离，渠道专属 SDK/签名/报文封闭在各自包内，新增渠道零改内核
- **零表结构**：框架不建表，持久化通过 Store SPI 交给使用方
- **双形态引入**：纯 jar 手动组装 / Spring Boot starter 自动装配
- **异步通知与对账**：回调验签 → 状态机 → 事件 → 下游 HTTP 通知；渠道账单与本地订单比对修正

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
conflux.pay:
  notify:
    retry-seconds: [15, 15, 30, 180, 600, 1200]
    secret: 下游通知签名密钥
  apps:
    app-001:
      channels:
        mock:
          notifyUrl: http://127.0.0.1:8088/callback/mock
          attrs:
            appKey: demo-app-key
            payOutcome: paying
```

实现并注册 `PaymentStore` 等 Store Bean，注入 `PaymentGateway` 即可下单。完整配置键、联调清单与回调示例见 [docs/config-keys.md](docs/config-keys.md)、[docs/integration-checklist.md](docs/integration-checklist.md)。

---

## 能力域二：数电发票（conflux-invoice）

参考支付域的 Ports & Adapters / Strategy 模式，把 `百旺 / 票通 / 航信 / 金蝶 / 企享云` 等服务商类比支付里的 `微信 / 支付宝`。但发票有支付没有的语义，因此内核不是支付模型的简单复制：

| 差异 | 支付 | 数电发票 | Conflux 的建模 |
|---|---|---|---|
| 企业准入 | 商户号即可 | 税号需准入、税局备案 | `sellerTaxNo` 作为配置第三维与路由键 |
| 通道分级 | 无 | 乐企直连 / 服务商 API / 电局网页 RPA 并存 | `ChannelTier` + `ChannelInstance` 择优路由 |
| 人工授权 | 无 | 扫脸 / 短信 / 扫码，登录态会过期 | `WAITING_AUTH` 挂起态 + `AuthSession` 端口 |
| 异步长耗时 | 少见 | 开票 40s+ 常转异步 | `PROCESSING` + 查单/回调推进 |
| 法定原件 | 无 | XML 是法定原件 | 无 XML 原件**拒绝**置为 `SUCCEEDED` |
| 红冲 | 退款一次调用 | 红字确认单需对方确认，两段长事务 | `RedApply` 独立实体 + 两段状态机 |
| 并发契约 | `update` 全量覆盖 | 回调与补单并发必须可表达 | Store 带 **CAS** 与**租约** |

### 状态机

```
CREATED → SUBMITTING → WAITING_AUTH ⇄ SUBMITTING → PROCESSING → SUCCEEDED
                                                        ↓
                              SUCCEEDED → RED_APPLYING → RED_APPLY_CONFIRMED → RED_SUCCEEDED
```

- `WAITING_AUTH` / `RED_APPLYING` 是**挂起态**，不是失败也不是终态，可被授权提交、查单或回调唤醒。
- `SUCCEEDED` **不是终态**（开票成功后仍可发起红冲）；真正终态是 `FAILED` / `CLOSED` / `RED_SUCCEEDED`。
- 所有迁移集中在 `InvoiceStateMachine` 一张显式表里，非法跃迁抛 `STATE_ILLEGAL`。

### Store 并发契约（对支付域契约缺位的修正）

```java
boolean tryUpdate(Invoice invoice, long expectedVersion);          // CAS：版本不匹配返回 false 且不写入
List<Invoice> claimProcessingBefore(LocalDateTime threshold, int limit,
                                    String owner, long leaseMillis); // 原子「查询 + 认领」，租约期内不重复领取
```

每次状态迁移都走 CAS 写入，失败抛 `STORE_CONCURRENT_MODIFICATION` 让补单/回调重试，避免回调线程与补单线程互相全量覆盖。`core` 的 `support` 包提供纯 JDK 内存参考实现（含深拷贝、租约、XML 归档），仅供单测与 example；生产请自行落库。

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
  mock-enabled: true
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

注册 4 个 Store Bean（`InvoiceStore` / `RedApplyStore` / `AuthSessionStore` / `InvoiceFileStore`），缺失时启动直接失败并给出提示。然后：

```java
InvoiceResult r = gateway.issue(new InvoiceRequest()
        .setAppId("demo").setBizOrderNo("B001")
        .setSellerTaxNo("91310000MOCK000001").setBuyerTitle("购方企业")
        .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
        .setItems(items).setAmount(new BigDecimal("1000.00")));

// r.getState() == WAITING_AUTH → 引导用户完成短信/扫码
InvoiceResult done = gateway.submitAuth(new AuthSubmission()
        .setSessionId(r.getAuthChallenge().getSessionId()).setCaptcha("123456"));
// done.getState() == SUCCEEDED，done.hasLegalXml() == true

RedApply apply = gateway.applyRed(...);   // 第一段：申请红字确认单（WAITING_CONFIRM）
gateway.confirmRed(apply.getRedApplyId()); // 对方确认 → CONFIRMED
gateway.issueRed(...);                     // 第二段：开红字发票 → 原票 RED_SUCCEEDED
```

`conflux-invoice-example` 提供可直接运行的 REST 宿主（`/api/invoices` 系列端点），默认 mock 渠道，零凭据即可跑通完整链路。辅助能力同样有端点：按票号查单 `GET /by-no`、发票交付重发 `POST /delivery`、发票修复 `POST /repair` 与结果查询 `POST /repair/query`、红字确认单主动确认/拒绝 `POST /red/confirm-action`。

**辅助能力为渠道可选能力**：新增的 `InvoiceDeliverer` / `InvoiceRepairer` 与 `InvoiceRedHandler` 两个可选方法一律 `default null`，未实现的渠道由注册表统一抛 `CHANNEL_CAPABILITY_NOT_SUPPORTED`，不影响 `mock` / 企享云等渠道；同一能力的 V1/V2 或同步/异步模式走 `attrs` 切换，不为版本单开 SPI 方法。

```java
gateway.resend(new DeliveryRequest().setInvoiceId(id).setEmail("a@b.com"));   // 短信/邮箱重发
RepairResult r = gateway.repair(new RepairRequest().setInvoiceId(id));          // 发票修复申请
gateway.queryRepair(new RepairRequest().setChannelOrderNo(r.getChannelOrderNo()));
gateway.confirmRed(new RedConfirmRequest().setRedApplyId(applyId).setAccept(true)); // 主动确认红字确认单
```

> 百旺金穗云按语雀《3.接口定义》逐条核对后，已实现 14 个 A/B 档接口（详见 [docs/invoice-platforms.md](docs/invoice-platforms.md) 第 3.5 节）；商品管理、机动车/二手车、乐企跨区、订单码、打印及**作废类**接口按「通用渠道平台 + 数电票只有红冲没有作废」的口径不纳入骨架。

---

## 发票平台接入现状（如实标注）

第一版先做「完全公开、有 API 文档」的平台。**框架不编造未经核实的 URL 与字段**：`RestChannelSpec.documentVerified` 标记文档核对状态，未核对的渠道默认不带任何接口路径、不暴露「假装能用」的能力，使用方按平台最新公开文档在 `attrs` 中配置路径与字段映射后即可启用。

**百旺金穗云已按用户指定的语雀官方公开文档完成专属适配器**：协议自带已核对的默认路径、网关签名与回调分流，不再走「先配路径再启用」的骨架流程，详见 [docs/invoice-platforms.md](docs/invoice-platforms.md) 第 3 节。

| 渠道编码 | 平台 | 通道级别 | 文档核对状态 | 已实现 |
|---|---|---|---|---|
| `mock` | 内置模拟 | SERVICE_API | 不适用 | 全能力：开票 / 授权 / 异步 / XML 原件 / 两段红冲 / 回调 |
| `qixiangyun` | 企享云 | SERVICE_API | **蓝字开具接口已核对** | `POST /v2/invoice/qdfp/fpkjZzs`，字段 `action/async/nsrsbh/ly_ddbh/wjgs/fplxdm/xmmx/jshj/hjse/data.xsf_yhmc`；响应 `reqId/code/success/message/data.fphm`，`code=2001` 转异步。查单/红字/授权/文件下载需按文档配置路径 |
| `baiwangjs` | 百旺金穗云 | SERVICE_API | **已核对（语雀官方文档）** | 专属适配器 `BaiwangJsChannelProvider`：Base64 报文 + HMAC 网关签名；蓝字开具 / 查单 / 发票修复 **V2 优先、V1 保留可切换**；红字两段（确认后开具 / 状态查询 / 主动确认）、登录 / 实人认证（同步+异步二维码）、3.4.15 / 3.4.8 版式文件查询、3.4.7 交付重发、3.3.17 可冲销校验、`hzcx` 回调分流、XML 法定原件。62 个协议层/编排层单测覆盖 |
| `piaotong` | 票通云 | SERVICE_API | **已核对（接口文档 3.2.0 + 生产实现交叉验证）** | 专属适配器 `PiaoTongChannelProvider`：明文信封 + 3DES 业务报文 + RSA 字典序签名 + 响应验签解密；蓝字开具（2.23~2.27 特定业务同端点自动附加）、查单、红字两段（2.38 新版优先 / 2.31 主动确认 / 2.29 查询 / 2.30 开具 / 2.33 局端兜底）、电局登录认证链路（2.4~2.8）、2.15 文件下载（XML/PDF/OFD）、2.13/2.14 加密推送回调、2.19 交付重发、2.12 全票面 / 2.45 账号列表便捷入口。32 个协议层单测覆盖 |
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
    qyId: "<企业唯一标识>"         # 3.1.1 查询一次后填入
    sellerTaxNo: "91310000XXXXXXXXXX"
    dlzh: "13800000000"            # 开票员电局登录账号
    kpr: "开票员"
    sqr: "申请人"
    apiVersion: "V2"               # 改 V1：开票/查单整体切到 V1 路径与合并字段报文
```

> 其余可覆盖路径键（`issuePath` / `queryPath` / `redApplyPath` / `redQueryPath` /
> `redIssuePath` / `redStatusPath` / `redConfirmPath` / `redAvailablePath` / `authStartPath` /
> `qrCodePath` / `qrCodeAsyncPath` / `riskInfoPath` / `deliveryPath` / `downloadPath` /
> `layoutQueryPath` / `repairPath` / `repairResultPath` / `queryBatchPath`）、
> `qrMode` / `repairResultMode` 模式开关、销方票面信息与票种编码映射见
> [docs/invoice-platforms.md](docs/invoice-platforms.md) 第 3.2 / 3.4 节与 example 的 `application.yml` 模板。
>
> 通用 REST 适配器的签名默认是 HMAC-SHA256 占位，各平台规则不同，正式接入前须按文档覆写 `buildHeaders`；
> 百旺金穗云已实现 `Base64(HmacSHA256(sign_str, SECRET))` 网关签名（`BaiwangJsSigner`），无需覆写。
> **所有渠道目前均为协议层实现 + 单测，尚未做真实平台联调。**

---

## 设计约定

- **错误码三段分层**：1xxx 通用 / 2xxx 支付 / 3xxx 发票，common 只持契约与通用段
- **账户键**：支付 `channelAppId#subMchNo`、发票 `channelAppId#sellerTaxNo`；回调 URL 带 `?accountKey=...`（`#` 需转义为 `%23`），装载期冲突 fail-fast
- **渠道实现无状态**：运行态数据一律从 `InvoiceContext` / 调用上下文取得，Provider 上不保存单据状态
- **传输层边界**：`HttpTransport` 只返回原始 `HttpResponse(statusCode, body, headers)`，非 2xx 不抛异常；业务判定、JSON/XML 解析、错误码翻译全部在渠道层
- **全栈 JDK 8**：不使用 `var` / `List.of` / `String.isBlank` 等 JDK 9+ API

## 构建与测试

```bash
mvn -q -DfailIfNoTests=false test
```

当前全量 **278 个用例全绿**（common 20、pay 76、invoice 182）。发票侧覆盖状态机、账户键、错误码分段、Store CAS/租约、渠道注册表、蓝字开票、授权挂起、异步查单、XML 法定原件强约束、两段红冲、回调归一、**版式文件固化（url/Base64 统一拉平为字节、可选落盘、XML strict 阻断、fileBytes 幂等回写与失效重拉）、按需转图与打印适配**、starter 装配与 example 端到端；其中百旺金穗云 **62 个用例**覆盖网关签名（`sign_str` 顺序 / GMT / HMAC-Base64 向量）、Base64 编解码、**V1/V2 报文与路径差异**、同步受理归一、回调 `ywlxdm` 分流（含 `hzcx`）、「票号回调 + 版式回调」两种到达顺序下编排层的收敛与票号不丢，以及 A/B 档新增的请求组装、修复结果归一、可选能力 `default-null` 契约与门面编排（`resend` / `repair` / `queryRepair` / 主动确认 / 红冲前置校验 / 按票号查单）；票通云 **32 个用例**覆盖 RSA 字典序签名串、3DES 往返、信封构建/验签解密（含篡改拒绝）、20/26 位流水号规则、开票报文组装（免税/优惠映射、金额位数）、业务状态归一（0000/6666/7777/9999/3999/4999/5999）、加密推送回调解析与加密应答信封、ThreadLocal 桥接的泄漏防护；common 侧 **PdfImageConverter 11 个用例**覆盖逐页转图（原尺寸/强制纸型/内容区域压缩）、栅格化 PDF 往返（页数与按 DPI 反算的页面尺寸）、Base64/流/文件入口、多页 ZIP 与单页纯图片输出、JPEG 透明铺底、PNG pHYs / JPEG JFIF DPI 元数据、非法输入魔数校验与配置边界。

## 文档索引

- [docs/config-keys.md](docs/config-keys.md) — 支付渠道配置键对照表与服务商模式回调路由
- [docs/integration-checklist.md](docs/integration-checklist.md) — 真实渠道联调清单
- [docs/invoice-platforms.md](docs/invoice-platforms.md) — 发票平台文档核对清单与接入说明（第 3 节为百旺金穗云专属适配器的协议、V2/V1 路径、签名、回调分流与配置全表）
