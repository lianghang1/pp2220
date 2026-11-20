# 发票域使用指南

统一数电发票 API：蓝字开票、授权、红冲（两段 + 快捷）、查单、交付、版式文件。SDK 不维护状态机、不暂存票据载体，状态语义由渠道返回、由使用方推进。

> 通用契约见 [getting-started.md](../getting-started.md)；各平台的协议核对与能力边界见 [reference/invoice-platforms.md](../reference/invoice-platforms.md)。

## 1. 引入

### Spring Boot starter（推荐）

```xml
<dependency>
    <groupId>io.github.lianghang1</groupId>
    <artifactId>conflux-invoice-spring-boot-starter</artifactId>
    <version>请替换为实际版本</version>
</dependency>
```

starter 自动装配 `InvoiceGateway`；内置渠道（mock / 百旺金穗云 / 百望云 / 票通云 / 诺诺）由 `build()` 经 SPI 清单全量自动注册，用户零 `.channel()`。

### 纯 jar / Spring MVC

```java
InvoiceGateway gateway = InvoiceGateway.builder()
        .configProvider(cp)        // 必填
        .build();                  // 内置渠道自动注册
```

core 与 channels 零 Spring 依赖；`conflux-invoice-example` 提供可直接运行的 REST 宿主，默认 mock 渠道，零凭据即可跑通完整链路。

## 2. 配置

配置路径 `conflux.invoice.configs.<configId>.<channelCode>`，例（mock）：

```yaml
conflux.invoice:
  configs:
    demo:
      mock:
        seller-tax-no: "91310000MOCK000001"
        notify-url: "http://127.0.0.1:8080/api/invoices/callback/mock"
        tier: SERVICE_API
        attr:
          appKey: demo-app-key
          appSecret: demo-secret
          issueOutcome: AUTH       # AUTH / ASYNC / SUCCESS / FAIL
          redConfirmOutcome: CONFIRMED
```

**发票特有维度**：

- **`sellerTaxNo`（销方税号）**：企业准入标识，也是多实例路由维度；同一 `configId` 下按税号 + 通道分级（`ChannelTier`）在乐企直连 / 服务商 API / 网页 RPA 间择优路由。
- **公共字段一等化**：`configId` / `channelCode` / `sellerTaxNo` / `channelInstanceId` / `notifyUrl` / `sandbox` / `tier`；渠道个性键放 `attr`。
- 配置对象为 `ChannelConfig` 基类 + 各渠道 `XxxParams extends ChannelConfig`，各渠道 `fromMap(attr)` 解析强类型参数；校验/映射规则见 [统一契约](../framework-contract.md)。

各平台专有配置键见 [reference/invoice-platforms.md](../reference/invoice-platforms.md)。

## 3. 核心调用

```java
InvoiceResult r = invoiceGateway.issue(new InvoiceRequest()
        .setConfigId("demo").setBizOrderNo("INV_001")
        .setSellerTaxNo("91310000MOCK000001").setBuyerTitle("购方企业")
        .setInvoiceType(InvoiceType.DIGITAL_VAT_GENERAL)
        .setItems(items).setAmount(new BigDecimal("1000.00")));
```

- **金额单位：元（`BigDecimal`）**。
- **票据载体往返**：`Invoice` / `RedApply` / `AuthSession` 随结果返回、随后续请求原样回传，由使用方保管；SDK 不暂存。

### 授权开票

```java
if (r.getState() == InvoiceState.WAITING_AUTH) {
    // 引导用户完成短信/扫码授权，随后回传会话与发票载体
    InvoiceResult done = invoiceGateway.submitAuth(new AuthSubmission()
            .setConfigId("demo").setSession(r.getAuthSession())
            .setInvoice(r.getInvoice()).setCaptcha("123456"));
    // done.getInvoice() 由使用方保存，红冲/交付要用
}
```

### 两段红冲

```java
RedApply apply = invoiceGateway.applyRed(new RedApplyRequest()
        .setConfigId("demo").setBizRedNo("RED_001").setOriginalInvoice(invoice));
RedApply confirmed = invoiceGateway.queryRedApply(new RedApplyQueryRequest()
        .setConfigId("demo").setRedApply(apply));
if (confirmed.getState() == RedApplyState.CONFIRMED) {
    InvoiceResult red = invoiceGateway.issueRed(new RedIssueRequest()
            .setConfigId("demo").setRedApply(confirmed).setOriginalInvoice(invoice));
}
// 也可用快捷红冲一次覆盖全流程：invoiceGateway.quickRed(...)，用 outcome 分流
```

> **状态语义**：SDK 只归一渠道状态，不做迁移约束判非法。挂起态由使用方保管载体，在回调/查单后重新发起相应调用。

## 4. 回调接入

验签仍在框架，配置定位由使用方：业务按业务单号查自己库得到 `configId` + `sellerTaxNo`，显式传入 `parseCallback(configId, sellerTaxNo, request)`，框架据此取配置验签，不隐式反查账户键。

```java
@PostMapping("/callback/{channelCode}")
public String callback(@PathVariable String channelCode, HttpServletRequest req) {
    // 1) 查自己 DB 得到 configId + sellerTaxNo
    String configId = /* ... */;
    String sellerTaxNo = /* ... */;

    InvoiceCallbackEvent event = invoiceGateway.parseCallback(configId, sellerTaxNo,
            new InvoiceCallbackRequest()
                    .setChannelCode(channelCode)
                    .setQueryParams(/* query */)
                    .setFormParams(/* form */)
                    .setBody(/* 原始 body */));

    // 2) 定位订单 → 判重 → 校验 → 推进状态（SDK 不介入）
    return invoiceGateway.callbackReply(channelCode, true, null);
}
```

部分平台公开文档未定义回调签名头/参数，SDK **不伪造验签规则**；这类渠道靠业务显式传参定位配置，生产环境应通过网络层白名单 / 网关校验保障。若宿主另行约定签名，可按平台文档用 `attr` 的签名键 + 查询参数启用校验（各平台支持情况见 [reference/invoice-platforms.md](../reference/invoice-platforms.md)）。

## 5. 版式文件与交付

- **XML 是法定原件**，无原件不置 `SUCCEEDED`；归档即固化由框架完成、使用方零感知。
- **统一字节出口**：`fileBytes(...)` 取原始字节；`pageImages(pdfBytes, config)` 逐页转图供预览；`printAdaptedPdf(pdfBytes, paper, dpi)` 栅格化重组指定纸型 PDF（解决特殊字体直接打印丢字）。转换时机与参数由使用方决定，框架不自动转换。
- 交付重发 `resend(...)`、修复 `repair(...)` 见 [API 参考](../reference/api-reference.md)。

## 6. 排错

- `CONFIG_MISSING`：确认 `configId` + `channelCode` + `sellerTaxNo` 三元组与配置一致。
- 红冲因缺 `sellerTaxNo` 定位失败：红冲请求需带销方税号用于配置定位。
- 挂起态不推进：检查是否保管并回传了 `AuthSession` / `RedApply` 载体。
- 平台字段级差异：查 [reference/invoice-platforms.md](../reference/invoice-platforms.md) 的对应渠道章节。
