# Conflux

可嵌入的**无状态渠道聚合 SDK**（**JDK 8+ / Spring Boot 2.7.x**），把支付、数电发票、短信、文件四域各家渠道的**方法名、出入参、返回结果、回调验签**归一成统一 API。

> **定位**：Conflux 不是独立运行的中间件，而是给成熟应用直接引入的 SDK。它只做一件事——统一渠道差异；**不持有台账、不做幂等/CAS、不维护状态机、不落库、不通知下游**。落库、判重、状态推进、下游通知、定时任务全部由使用方在自己的业务链里完成。

## 四域速览

| 能力域 | 门面 | 核心能力 | 使用指南 |
|---|---|---|---|
| 支付 `conflux-pay` | `PaymentGateway` | 下单/查单/关单、退款、转账、分账、账单、回调验签 | [guides/pay.md](docs/guides/pay.md) |
| 数电发票 `conflux-invoice` | `InvoiceGateway` | 蓝字开票、授权、红冲、查单、交付、版式文件 | [guides/invoice.md](docs/guides/invoice.md) |
| 短信 `conflux-sms` | `SmsGateway` | 发送短信（内部委托 sms4j，22 家厂商开箱即用） | [guides/sms.md](docs/guides/sms.md) |
| 文件 `conflux-file` | `FileGateway` | 上传/下载/删除/预签名（内部委托 x-file-storage，30+ 平台） | [guides/file.md](docs/guides/file.md) |

## 支付渠道矩阵

| 渠道 | 支付方式 | 退款 | 转账 | 分账 | 账单 | 回调验签 |
|---|---|---|---|---|---|---|
| wechat | JSAPI / 小程序 / Native / H5 / APP / 付款码，V2·V3 双协议 | ✓ | ✓ | ✓ | ✓ | V3 平台证书 / V2 MD5 |
| alipay | 扫码 / WAP / 电脑网站 / APP / JSAPI / 付款码 | ✓ | ✓ | ✓ | ✓ | RSA2 公钥 |
| unionpay | 银联二维码·付款码 / 转接微信·支付宝 | ✓ | — | — | — | 平台公钥 |
| paypal | Orders v2（批准→捕获） | ✓ | Payouts | — | — | Webhook 官方验签 |
| chinaums | 微信/支付宝/云闪付 × APP/H5/小程序/公众号 + C扫B主扫 | ✓ | — | — | — | 回调触发主动查单 |
| mock | 二维码模拟 | ✓ | ✓ | ✓ | — | 自定义头 |

发票平台支持现状见 [reference/invoice-platforms.md](docs/reference/invoice-platforms.md)。

## 五条核心约定

- **SDK 无状态**：所有 `xxxResult` 只是渠道归一后的结果快照；`Invoice` / `RedApply` / `AuthSession` 等票据载体随结果返回、随后续请求回传，由使用方保管。
- **受理 ≠ 终态**：下单/开票返回的 `state` 是渠道**受理态**；最终态来自**回调**或**主动查单**。使用方必须实现回调入口或定时查单（至少其一）。
- **判重与金额校验在使用方**：回调只验签 + 归一，业务单号定位、判重、金额比对、状态推进都是业务的事。
- **回调配置定位显式化**：验签在框架，配置定位在使用方——业务查自己 DB 得到 `configId`（发票再加 `sellerTaxNo`）显式传入 `parseCallback`，框架据此取配置验签，不隐式反查账户键。
- **金额单位**：支付 = **分**（`Long`）；发票 = **元**（`BigDecimal`）。

## 快速开始

用一个最小可跑的支付配置感受范式（完整教程见 [getting-started.md](docs/getting-started.md) 与各域指南）：

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>conflux-pay-spring-boot-starter</artifactId>
    <version>请替换为实际版本</version>
</dependency>
```

```yaml
conflux.pay.configs.demo.mock:
  notifyUrl: http://127.0.0.1:8088/callback/mock
  attr:
    appKey: demo-app-key
```

```java
@Autowired private PaymentGateway paymentGateway;

PaymentResult pay = paymentGateway.createPayment(new PaymentRequest()
        .setConfigId("demo").setBizOrderNo("ORDER_001")
        .setMethod(PayMethod.MOCK_PAY.getCode())
        .setAmount(9900L).setSubject("订单支付"));
```

内置渠道由 `builder().build()` 自动全量注册，用户零 `.channel()`；四域用同一套门面范式（`builder()` / `.configProvider(...)` / `registerChannel()` / `registeredChannels()`）。

## 模块构成

| 模块 | 说明 | Spring 依赖 |
|---|---|---|
| `conflux-common` | 公共工具 / 异常 / HTTP 传输 / 配置映射 | 无 |
| `conflux-pay`（core / channels / starter / example） | 支付域 | core·channels 无；starter 需 Boot |
| `conflux-invoice`（core / channels / starter / example） | 发票域 | core·channels 无；starter 需 Boot |
| `conflux-sms`（core / starter） | 短信域，内部委托 sms4j 原始包 | core 无；starter 需 Boot |
| `conflux-file`（core / starter） | 文件域，内部委托 x-file-storage 原始包 | core 无；starter 需 Boot |

Spring Boot 用 starter 自动装配；Spring MVC / 传统 Spring / 纯 Java 引入对应 core（+ channels）手动 `builder()` 组装，配置来源不限配置文件。详见 [getting-started.md](docs/getting-started.md)。

## 文档索引

**使用者入口**
- [getting-started.md](docs/getting-started.md) — 通用入门：三种引入形态、配置模型、回调定位、四域共性
- 四域使用指南：[支付](docs/guides/pay.md) · [发票](docs/guides/invoice.md) · [短信](docs/guides/sms.md) · [文件](docs/guides/file.md)

**参考手册**
- [reference/api-reference.md](docs/reference/api-reference.md) — 四域 API 方法 / 出入参字段 / 枚举速查
- [reference/config-keys.md](docs/reference/config-keys.md) — 支付渠道配置键对照表
- [reference/invoice-platforms.md](docs/reference/invoice-platforms.md) — 发票平台文档核对与能力清单
- [integration-checklist.md](docs/integration-checklist.md) — 真实渠道联调清单

**维护者规范**
- [framework-contract.md](docs/framework-contract.md) — 四域统一契约（图纸 / 规范 / 检查清单），新增能力或渠道必须严格执行

## 构建与测试

```bash
mvn -q -DfailIfNoTests=false test
```

当前全量 **314 个用例全绿**。
