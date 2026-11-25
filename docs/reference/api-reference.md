# API 参考

统一入口是五个门面类，五域对外契约同构，双形态（Spring Boot 注入 / 纯 jar `builder()`）方法完全相同。支付/发票的请求/结果类位于 `*.api` 包、状态枚举位于 `*.model` 包，链式 setter（`@Accessors(chain=true)`）；文件域门面直接返回 x-file-storage 官方原始类型；登录域统一身份模型为 `AuthUser`。

五域配置的框架定位键统一为 `configId`（发票另需 `sellerTaxNo`），渠道统一用 `channelCode`，渠道个性配置统一放 `attr`。回调验签仍在框架（登录域为换身份），但配置定位由使用方按自己的订单查库得到 `configId` 后显式传入，框架不再从报文隐式反查账户键。

| 门面 | 全限定名 | 获取方式 |
|---|---|---|
| 支付 | `com.sfi.conflux.pay.engine.PaymentGateway` | Spring 注入，或 `PaymentGateway.builder()....build()` |
| 发票 | `com.sfi.conflux.invoice.engine.InvoiceGateway` | Spring 注入，或 `InvoiceGateway.builder()....build()` |
| 短信 | `com.sfi.conflux.notify.engine.NotificationGateway` | Spring 注入，或 `NotificationGateway.builder()....build()` |
| 文件 | `com.sfi.conflux.file.engine.FileGateway` | Spring 注入，或 `FileGateway.builder()....build()` |
| 登录 | `com.sfi.conflux.auth.engine.AuthGateway` | Spring 注入，或 `AuthGateway.builder()....build()` |

金额单位：支付 = 分（`Long`），发票 = 元（`BigDecimal`）。

五域门面的框架契约一致：均经 `builder()` 构建、`builder().configProvider(...)` 注入配置源（`ChannelConfigProvider` / `InvoiceConfigProvider` / `NotificationConfigProvider` / `FileConfigProvider` / `AuthConfigProvider`，均可自定义 DB / 配置中心实现）、`registerChannel(...)` 装载自定义渠道、`registeredChannels()` 查看已装载渠道，配置源每次调用读取、凭证变更可重建客户端。**内置渠道由 `build()` 自动全量注册，用户零 `.channel()`；`.channel(...)` 仅作自定义渠道扩展口，同编码覆盖内置。** 差异仅在支付/发票/登录有自有渠道 SPI（登录为 `AuthChannel`），短信/文件渠道实现全在第三方原始包（sms4j / x-file-storage）。

## 一、支付 API（PaymentGateway）

### 1.1 方法清单

| 方法 | 入参 | 返回 | 说明 |
|---|---|---|---|
| `createPayment` | `PaymentRequest` | `PaymentResult` | 统一下单，返回唤起参数或即时扣款结果 |
| `queryPayment` | `PaymentQueryRequest` | `PaymentResult` | 渠道查单快照 |
| `closePayment` | `PaymentQueryRequest` | `PaymentResult` | 关单；渠道报已支付时复查一次并按真实状态返回 |
| `createRefund` | `RefundRequest` | `RefundResult` | 发起退款（须带原单金额、原单渠道单号） |
| `queryRefund` | `RefundQueryRequest` | `RefundResult` | 退款查单 |
| `createTransfer` | `TransferRequest` | `TransferResult` | 发起转账 |
| `queryTransfer` | `TransferQueryRequest` | `TransferResult` | 转账查单 |
| `createProfitShare` | `ProfitShareRequest` | `ProfitShareResult` | 发起分账（须带原支付单渠道单号） |
| `queryProfitShare` | `ProfitShareQueryRequest` | `ProfitShareResult` | 分账查单 |
| `parseCallback` | `String configId, ChannelCallbackRequest` | `CallbackEvent` | 按 configId 取配置验签并归一回调事件（configId 由使用方查库显式传入） |
| `callbackSuccessReply` | `String channelCode` | `String` | 处理成功应答报文 |
| `callbackFailureReply` | `String channelCode` | `String` | 处理失败应答报文（触发渠道重推） |
| `fetchBill` | `String channelCode, String configId, LocalDate billDate` | `BillSheet` | 拉取渠道账单，对账比对由使用方完成 |
| `registerChannel` | `ChannelProvider` | `void` | 启动期注册渠道提供者 |
| `registeredChannels` | — | `Set<String>` | 已注册渠道编码集合 |
| `builder` | — | `Builder` | 创建构建器 |

回调应答报文由 `callbackSuccessReply` / `callbackFailureReply` 按渠道生成，使用方直接把返回值原样写回响应体即可。各渠道取值：

| 渠道 | 成功应答 | 失败应答 |
|---|---|---|
| `wechat` | `{"code":"SUCCESS","message":"成功"}` | `{"code":"FAIL","message":"失败"}` |
| `alipay` | `success` | `fail` |
| `chinaums`（天满） | `SUCCESS` | `FAILED` |
| `unionpay` | `success` | `fail` |
| `paypal` | `OK` | `FAIL` |

> 天满失败应答必须是 `FAILED`（非 `FAIL`），依据官方「支付结果通知」规则；写错会导致渠道认为商户应答无效并在 24 小时内重复重推通知。天满回调采用「触发主动查单确认」，不验通知里的签名，详见 [支付域使用指南](../guides/pay.md)。

### 1.2 请求对象字段

| 类 | 字段 |
|---|---|
| `PaymentRequest` | `configId` `bizOrderNo` `method` `channelCode` `amount` `currency` `subject` `body` `clientIp` `notifyUrl` `channelExtras` |
| `PaymentQueryRequest` | `configId` `bizOrderNo` `channelCode` `channelOrderNo` |
| `RefundRequest` | `configId` `bizRefundNo` `channelCode` `bizOrderNo` `channelOrderNo` `totalAmount` `amount` `currency` `reason` `notifyUrl` `channelExtras` |
| `RefundQueryRequest` | `configId` `bizRefundNo` `channelCode` `channelRefundNo` `bizOrderNo` |
| `TransferRequest` | `configId` `bizTransferNo` `method` `payeeAccount` `payeeName` `amount` `currency` `remark` `notifyUrl` `channelExtras` |
| `TransferQueryRequest` | `configId` `bizTransferNo` `channelCode` `channelTransferNo` |
| `ProfitShareRequest` | `configId` `bizShareNo` `channelCode` `bizOrderNo` `channelOrderNo` `receivers`（`List<ProfitShareReceiver>`）`channelExtras` |
| `ProfitShareQueryRequest` | `configId` `bizShareNo` `channelCode` `channelShareNo` |
| `ChannelCallbackRequest` | `channelCode` `headers` `queryParams` `formParams`（均 `Map<String,String>`）`body` |

> **异步通知地址（notifyUrl）**：支付下单 / 退款 / 转账的请求均可选传 `notifyUrl`，作为一等字段随 `Payment`/`Refund`/`Transfer` 载体透传给渠道 handler；handler 按「请求非空优先、否则回退渠道配置的 `notifyUrl`」取值。同一渠道可按单指定不同回调地址，不传则完全沿用配置。

### 1.3 结果对象字段

| 类 | 字段 |
|---|---|
| `PaymentResult` | `configId` `bizOrderNo` `method` `channelCode` `channelOrderNo` `state` `payPayload` `errorCode` `errorMsg` `succeedTime` |
| `RefundResult` | `configId` `bizRefundNo` `amount` `currency` `state` `channelRefundNo` `errorCode` `errorMsg` `succeedTime` |
| `TransferResult` | `configId` `bizTransferNo` `method` `channelCode` `state` `channelTransferNo` `errorCode` `errorMsg` `succeedTime` |
| `ProfitShareResult` | `configId` `bizShareNo` `amount` `receivers` `state` `channelShareNo` `errorCode` `errorMsg` `finishTime` |
| `CallbackEvent` | `type`（`BizOrderType`）`configId` `bizOrderNo` `channelOrderNo` `success` `amount` `occurTime` `raw` |
| `BillSheet` | `configId` `channelCode` `billDate` `entries`（`List<BillEntry>`） |

> `PaymentResult.payPayload` 的结构与使用方式见 [§1.5 支付唤起参数](#15-支付唤起参数paypayload)。

### 1.4 支付方式与状态枚举

`method` 取值（`PayMethod`）：

`WECHAT_JSAPI` `WECHAT_LITE` `WECHAT_NATIVE` `WECHAT_H5` `WECHAT_APP` `WECHAT_BAR` `WECHAT_TRANS` / `ALI_QR` `ALI_WAP` `ALI_PAGE` `ALI_APP` `ALI_JSAPI` `ALI_BAR` `ALI_TRANSFER` / `UNION_QR` `UNION_BAR` `UNION_WX_JSAPI` `UNION_WX_BAR` `UNION_ALI_JSAPI` `UNION_ALI_BAR` / `PAYPAL_SALE` `PAYPAL_PAYOUT` / `CHINAUMS_WX_APP` `CHINAUMS_WX_H5` `CHINAUMS_WX_MINI` `CHINAUMS_WX_JSAPI` `CHINAUMS_ALI_APP` `CHINAUMS_ALI_H5` `CHINAUMS_ALI_MINI` `CHINAUMS_ALI_JSAPI` `CHINAUMS_UAC_APP` `CHINAUMS_UAC_H5` `CHINAUMS_UAC_MINI` `CHINAUMS_UAC_JSAPI` `CHINAUMS_QR` / `MOCK_PAY` `MOCK_TRANS`

`channelCode` 取值（`ChannelCode`）：`wechat` `alipay` `unionpay` `paypal` `chinaums` `mock`

| 枚举 | 取值 |
|---|---|
| `PaymentState` | `CREATED` `PAYING` `SUCCEEDED` `FAILED` `CLOSED` `REFUNDING` `PART_REFUNDED` `REFUNDED` |
| `RefundState` | `CREATED` `PROCESSING` `SUCCEEDED` `FAILED` `CLOSED` |
| `TransferState` | `CREATED` `PROCESSING` `SUCCEEDED` `FAILED` |
| `ProfitShareState` | `CREATED` `PROCESSING` `FINISHED` `FAILED` |

### 1.5 支付唤起参数（PayPayload）

不同渠道、不同支付方式在下单成功后返回给使用方的"唤起要素"形态不同：有的给二维码码值，
有的给跳转链接，有的给 JSAPI/APP 参数，付款码类甚至没有唤起参数（下单即扣款）。
SDK 用统一的 `PayPayload`（`com.sfi.conflux.pay.api.PayPayload`，仅 `type` + `content` 两字段）
承载这些差异——使用方只需按 `type` 分支处理，无需为每种渠道写不同的返回解析：

| `PayPayloadType`（`*.model`） | `content` 语义 | 使用方动作 | 典型支付方式 |
|---|---|---|---|
| `QRCODE` | 二维码**内容/码值** | 用 content 生成二维码图片展示，让用户扫码 | `WECHAT_NATIVE` `ALI_QR` `CHINAUMS_QR` `UNION_QR` `MOCK_PAY` |
| `REDIRECT` | 支付页 **URL** | 引导用户在浏览器/内置 WebView 跳转该 URL | `WECHAT_H5` `ALI_WAP` `ALI_PAGE` `PAYPAL_SALE` `CHINAUMS_*_H5` |
| `JSAPI` | JSON 对象串（公众号/小程序唤起参数） | 解析 JSON，传给对应 JS SDK 拉起支付 | `WECHAT_JSAPI` `WECHAT_LITE` `ALI_JSAPI` `CHINAUMS_*_MINI` `CHINAUMS_*_JSAPI` |
| `APP` | JSON 对象串（APP 支付参数） | 解析 JSON，传给 App 端 SDK 拉起支付 | `WECHAT_APP` `ALI_APP` `CHINAUMS_*_APP` |
| `NONE` | 空 | 无需唤起——下单即完成支付 | `WECHAT_BAR` `ALI_BAR` `UNION_*_BAR` 等付款码方式 |

**易混点：二维码内容 vs 跳转链接。** `QRCODE` 的 content 即使长得像 URL（如支付宝
`https://qr.alipay.com/...`、微信 `weixin://wxpay/...`），它仍是**要渲染成二维码的内容**，
不是让浏览器直接跳转的地址；真正要跳转的用 `REDIRECT`。使用方务必以 `type` 判断，
不要按 content 是否以 `http` 开头去猜。

**与 `state` 配合：** `state=PAYING` 时按上表消费 `payPayload` 唤起用户支付；
`state=SUCCEEDED` 表示即时扣款已完成（`payPayload` 通常为 `NONE`）；
`state=CREATED` 表示渠道调用尚未成功（可携带原参数重试下单），此时不消费 payload。

```java
PaymentResult result = gateway.createPayment(request);
if (result.getState() == PaymentState.SUCCEEDED) {
    // 付款码等即时扣款：已完成，无需唤起
} else if (result.getState() == PaymentState.PAYING) {
    PayPayload p = result.getPayPayload();
    switch (p.getType()) {
        case QRCODE:   renderQrCode(p.getContent()); break;         // 生成二维码给用户扫
        case REDIRECT: redirectTo(p.getContent()); break;           // 浏览器跳转
        case JSAPI:    callJsSdk(parseJson(p.getContent())); break; // 公众号/小程序
        case APP:      callAppSdk(parseJson(p.getContent())); break;// App SDK
    }
}
```
（以上为消费示意，方法名按使用方实际实现。）

## 二、发票 API（InvoiceGateway）

### 2.1 方法清单

| 方法 | 入参 | 返回 | 说明 |
|---|---|---|---|
| `issue` | `InvoiceRequest` | `InvoiceResult` | 蓝字开票，返回受理结果与发票载体 |
| `query` | `InvoiceQueryRequest` | `InvoiceResult` | 渠道查单快照与发票载体 |
| `submitAuth` | `AuthSubmission` | `InvoiceResult` | 提交授权凭证，通过后重新开票 |
| `applyRed` | `RedApplyRequest` | `RedApply` | 申请红字确认单（两段红冲第一段） |
| `queryRedApply` | `RedApplyQueryRequest` | `RedApply` | 查确认单确认状态 |
| `confirmRed` | `RedConfirmRequest` | `RedApply` | 主动确认/拒绝确认单（渠道可选能力） |
| `issueRed` | `RedIssueRequest` | `InvoiceResult` | 确认后开红票（两段红冲第二段） |
| `quickRed` | `RedApplyRequest` | `QuickRedResult` | 快捷红冲，`outcome` 区分走到哪一步 |
| `resend` | `DeliveryRequest` | `DeliveryResult` | 重新交付（短信/邮箱重发），不改状态 |
| `repair` | `RepairRequest` | `RepairResult` | 提交发票修复申请，不改状态 |
| `queryRepair` | `RepairRequest` | `RepairResult` | 查修复结果 |
| `parseCallback` | `String configId, String sellerTaxNo, InvoiceCallbackRequest` | `InvoiceCallbackEvent` | 按 configId + 销方税号取配置验签并归一回调事件（由使用方查库显式传入） |
| `callbackReply` | `String channelCode, boolean success, String message` | `InvoiceCallbackReply` | 生成应答报文 |
| `materializeFiles` | `List<InvoiceFile> files, String invoiceId, boolean strict` | `List<InvoiceFile>` | 就地固化版式文件（下载/解码回填） |
| `fileBytes` | `InvoiceFile file` / `List<InvoiceFile> files, InvoiceFileType type` | `byte[]` | 取文件字节（内联解码；仅 url 时下载） |
| `pageImages` | `byte[] pdfBytes, PdfImageConverter.Config config` | `List<PageImage>` | 按需：PDF 逐页转图 |
| `printAdaptedPdf` | `byte[] pdfBytes, PaperSize paper, int dpi` | `byte[]` | 按需：转图后重组指定纸型 PDF |
| `registerChannel` | `InvoiceChannelProvider` | `void` | 启动期注册渠道提供者 |
| `registeredChannels` | — | `Set<String>` | 已注册渠道编码集合 |
| `builder` | — | `Builder` | 创建构建器 |

### 2.2 请求对象字段

| 类 | 字段 |
|---|---|
| `InvoiceRequest` | `configId` `bizOrderNo` `channelCode` `sellerTaxNo` `sellerName` `buyerTaxNo` `buyerTitle` `buyerAddress` `buyerBank` `buyerPhone` `buyerMobile` `buyerEmail` `invoiceType` `items`（`List<InvoiceItem>`）`amount` `taxAmount` `remark` `drawer` `tspz` `zsfs` `jzfwTdys` `bdcTdysList` `notifyUrl` `sandbox` `attr` |
| `InvoiceQueryRequest` | `configId` `channelCode` `bizOrderNo` `channelOrderNo` `invoiceNo` `invoiceCode` `invoice` |
| `AuthSubmission` | `configId` `captcha` `token` `extra` `session`（`AuthSession`）`invoice` |
| `RedApplyRequest` | `configId` `bizRedNo` `reason` `chyydm` `amount` `channelCode` `notifyUrl` `originalInvoice` |
| `RedApplyQueryRequest` | `configId` `redApply` |
| `RedConfirmRequest` | `configId` `accept` `reason` `extra` `redApply` |
| `RedIssueRequest` | `configId` `channelCode` `notifyUrl` `redApply` `originalInvoice` |
| `DeliveryRequest` | `configId` `phone` `email` `extra` `invoice` |
| `RepairRequest` | `configId` `channelCode` `sellerTaxNo` `invoiceId` `invoiceCode` `invoiceNo` `startDate` `endDate` `invoiceTypeCode` `channelOrderNo` `extra` |
| `InvoiceCallbackRequest` | `channelCode` `queryParams` `formParams` `headers`（均 `Map<String,String>`）`body` |

### 2.3 结果对象字段

| 类 | 字段 |
|---|---|
| `InvoiceResult` | `invoiceId` `bizOrderNo` `state` `channelCode` `channelInstanceId` `channelOrderNo` `invoiceNo` `invoiceCode` `invoiceDate` `invoiceTime` `amount` `taxAmount` `files` `authChallenge` `asyncAccepted` `errorCode` `errorMsg` `rawPayload` `invoice` `authSession` |
| `QuickRedResult` | `outcome`（`ACCEPTED` / `WAITING_CONFIRM`）`redApply` `redInvoice` |
| `RedApply` | `redApplyId` `configId` `bizRedNo` `originalInvoiceId/No/Code` `reason` `chyydm` `amount` `channelCode` `channelInstanceId` `channelOrderNo` `redConfirmNo` `state` `confirmDeadline` `confirmedTime` `redInvoiceId` `notifyUrl` `errorCode` `errorMsg` |
| `DeliveryResult` | `accepted` `asyncAccepted` `channelOrderNo` `errorCode` `errorMsg` `rawPayload` |
| `RepairResult` | `channelOrderNo` `files` `invoiceNo` `invoiceCode` `errorCode` `errorMsg` `rawPayload` |
| `AuthSession` | `sessionId` `invoiceId` `configId` `channelCode` `authType` `state` `challengeUrl` `challengeText` `expireTime` `submittedTime` `payload` |
| `InvoiceCallbackEvent` | `invoiceId` `redApplyId` `channelOrderNo` `bizOrderNo` `state` `invoiceNo` `invoiceCode` `invoiceDate` `amount` `taxAmount` `files` `redState` `redConfirmNo` `errorCode` `errorMsg` `rawPayload` `attr` |
| `InvoiceCallbackReply` | `success` `contentType` `body` `message` |

### 2.4 票据载体与枚举

载体随结果返回、随后续请求回传，SDK 不暂存：`Invoice`、`RedApply`、`AuthSession`、`InvoiceFile`。

| 枚举 | 取值 |
|---|---|
| `InvoiceState` | `CREATED` `SUBMITTING` `WAITING_AUTH` `PROCESSING` `SUCCEEDED` `FAILED` `CLOSED` `RED_APPLYING` `RED_APPLY_CONFIRMED` `RED_SUCCEEDED` |
| `RedApplyState` | `APPLYING` `WAITING_CONFIRM` `CONFIRMED` `REJECTED` `EXPIRED` `ISSUED` `FAILED` |
| `AuthSessionState` | `PENDING` `SUBMITTED` `VERIFIED` `EXPIRED` `FAILED` |
| `AuthType` | `NONE` `SMS` `FACE` `QR` `LOGIN` |
| `InvoiceType` | `DIGITAL_VAT_SPECIAL` `DIGITAL_VAT_GENERAL` `VAT_SPECIAL` `VAT_GENERAL` `VEHICLE_SALES` `OTHER` |
| `ChannelTier` | `DIRECT_TAX` `SERVICE_API` `WEB_PORTAL` |
| `InvoiceFileType` | `XML` `OFD` `PDF` `IMAGE` |

## 三、通知 API（NotificationGateway）

通知域门面只做路由：内部一张 `Map<channelCode, NotificationChannel>` 统一渠道表，一条 code 只对应一个渠道。所有消息形态（短信、微信全家桶、支付宝、飞书）以统一 `NotificationMessage` 入参，`channelCode` 由消息自带；门面只有唯一发送入口 `send(configId, message)`。内置渠道（短信 sms4j + 微信三大平台 + 支付宝小程序/生活号 + 飞书）经 SPI 清单在 build 期自动注册，用户零 `.channel()` 即可用。

### 3.1 方法清单

| 方法 | 入参 | 返回 | 说明 |
|---|---|---|---|
| `send` | `String configId, NotificationMessage` | `NotificationSendResult` | 唯一发送入口：按 configId + 消息自带 channelCode 读配置 → 找渠道 → 转发 |
| `registerChannel` | `NotificationChannel` | `void` | 声明自定义通知渠道，加入统一渠道表（同编码覆盖内置） |
| `registerChannel` | `SmsChannelProvider` | `void` | 声明自定义短信渠道：把 sms4j 原生 Factory 注册进 `ProviderFactoryHolder`，包装为 `Sms4jChannel` 加入统一渠道表 |
| `registeredChannels` | — | `Set<String>` | 统一渠道表已装载编码集合（短信 + 内置 IM + 自定义） |
| `builder` | — | `Builder` | 创建构建器 |

`NotificationGateway.Builder` 方法：`.configProvider(NotificationConfigProvider)`（**必填**，与支付/发票一致；send 每次读配置，实现 `allConfigs()` 时构建期预热）、`.channel(NotificationChannel)`（声明自定义通知渠道）与 `.channel(SmsChannelProvider)`（声明自定义短信渠道 Factory，可多次调用）、`.build()`（缺 configProvider 抛 `CONFIG_MISSING`；SPI 自动注册内置 IM 渠道 + 按配置预热短信渠道）。

`SmsChannelProvider.of(BaseProviderFactory factory)` 接入清单外厂商——按 [sms4j 官方自定义扩展](https://sms4j.com/doc3/custom.html) 实现的 Factory 实例直接传入。

### 3.2 消息 / 结果与配置

| 类型 | 字段 / 说明 |
|---|---|
| `NotificationMessage` | 统一消息接口，仅 `channelCode()`；所有消息实现它，编码 = 凭证/应用形态（C5） |
| `NotificationSendRequest` | 短信消息（实现 `NotificationMessage`）：`channelCode`(sms4j 供应商码) `bizId` `phone` `content` `params` |
| 微信消息 bean | 见 3.3，继承 wxjava 原生参数类并实现 `NotificationMessage` |
| `NotificationSendResult` | `success` `bizId` `configId` `channel` `raw`（SDK 原始返回） `error` |
| `NotificationConfig` | `configId` `channelCode` `accessKeyId` `accessKeySecret` `signature` `templateId` `attr`（厂商/平台特有键） |

`NotificationConfig` 是通知域自有配置对象，**不暴露任何第三方官方配置类**。短信用公共凭证四件套一等字段 + `mergedAttr()` 交 sms4j 官方 Factory；微信凭证（appId/secret/corpId 等）按 C2 放 `attr`，由渠道映射为平台层 `PlatformCredential`，不复用短信四件套。

配置源 SPI：`NotificationConfigProvider.getConfig(configId, channelCode)` 返回 `NotificationConfig`，查不到抛 `GatewayException(CONFIG_MISSING)`；内置 `InMemoryNotificationConfigProvider`（纯 Java/测试）与 starter 的 `PropertiesNotificationConfigProvider`（读 `conflux.notify.configs.<configId>.<channelCode>`）。发送即终态，SDK 不持有台账、不判重、不做频率控制。

### 3.3 微信 / 支付宝 / 飞书消息（conflux-notify-channels）

消息 bean 仅额外实现 `NotificationMessage.channelCode()`,一份平台凭证覆盖该应用全部消息类型,渠道按 bean 类型分发到对应 SDK 方法。三家 SDK 形态不同,适配方式各异：微信 bean **继承 wxjava 原生参数类**直接传 service；支付宝 bean 继承原生 Model（`AlipayObject`）,渠道内 `setBizModel` 包装后 `execute`；飞书 SDK 的 content 是 JSON 字符串、无强类型入参 bean,bean 承载结构化字段、渠道内用 common `JsonUtils` 序列化成 content。

| channelCode | 消息 bean（继承原生类） | SDK 调用 |
|---|---|---|
| `wechat_mp` | `MpTemplateMsg` / `MpKefuMsg` / `MpSubscribeMsg` / `MpMassTagMsg` / `MpMassOpenIdsMsg` / `MpMassPreviewMsg` | `WxMpTemplateMsgService.sendTemplateMsg` / `WxMpKefuService.sendKefuMessage` / `WxMpSubscribeMsgService.send` / `WxMpMassMessageService.massXxx` |
| `wechat_mini` | `MiniSubscribeMsg` / `MiniKefuMsg` / `MiniUniformMsg` / `MiniUpdatableMsg` | `WxMaMsgService.sendSubscribeMsg` / `sendKefuMsg` / `sendUniformMsg` / `setUpdatableMsg` |
| `wechat_wecom` | `WeComAppMsg` / `WeComAppChatMsg` / `WeComLinkedCorpMsg` | `WxCpMessageService.send` / `WxCpChatService.sendMsg` / `sendLinkedCorpMessage` |
| `wechat_wecom`（群机器人） | `WeComRobotTextMsg` / `WeComRobotMarkdownMsg` / `WeComRobotImageMsg` / `WeComRobotFileMsg` / `WeComRobotVoiceMsg` / `WeComRobotNewsMsg` / `WeComRobotCardMsg` | `WxCpGroupRobotService.sendText/sendMarkdown/sendImage/sendFile/sendVoice/sendNews/sendTemplateCardMessage`；前五种收散装 String，由渠道拆参；后两种可继承原生 bean 直接传 |

```java
// 小程序订阅消息：一份 wechat_mini 凭证，选 bean 决定消息类型
MiniSubscribeMsg msg = new MiniSubscribeMsg();
msg.setToUser("openid");
msg.setTemplateId("tpl_xxx");
NotificationSendResult r = gateway.send("app1", msg);   // → wechat_mini 凭证 + 订阅消息
```

支付宝消息（继承原生 Model，渠道内 setBizModel + execute）：

| channelCode | 消息 bean（继承原生 Model） | SDK 调用 |
|---|---|---|
| `alipay_mini` | `AliMiniSubscribeMsg` / `AliMiniBatchSubscribeMsg` | `AlipayOpenAppMiniTemplatemessageSendRequest` / `...BatchsendRequest` |
| `alipay_public` | `AliPublicSingleMsg` / `AliPublicLabelMsg` / `AliPublicGroupMsg` / `AliPublicPreviewMsg` | `AlipayOpenPublicMessageSingleSendRequest` / `...LabelSendRequest` / `...GroupSendRequest` / `...PreviewSendRequest` |

```java
// 支付宝小程序订阅消息：与登录域复用 alipay_mini 凭证
AliMiniSubscribeMsg msg = new AliMiniSubscribeMsg();
msg.setToOpenId("openid-1");
msg.setUserTemplateId("TPL_1");
NotificationSendResult r = gateway.send("app1", msg);   // → alipay_mini 凭证 + 订阅消息
```

飞书消息（content 由 bean 字段序列化）：

| channelCode | 消息 bean | SDK 调用 |
|---|---|---|
| `feishu` | `FeishuTextMsg` / `FeishuPostMsg` / `FeishuCardMsg` / `FeishuImageMsg` / `FeishuFileMsg` | `client.im().message().create(CreateMessageReq)`；msgType = text/post/interactive/image/file，content = bean 字段经 JsonUtils 序列化 |

```java
// 飞书文本消息
FeishuTextMsg msg = new FeishuTextMsg();
msg.setReceiveId("ou_xxx");           // 默认 open_id
msg.setText("你好飞书");
NotificationSendResult r = gateway.send("app1", msg);   // → feishu 凭证
```

> 编码粒度 = 一份凭证/应用形态，不是消息类型：同一小程序 appId 的订阅/客服/统一消息共用 `wechat_mini`，由 bean 类型区分。企业微信归 `wechat_wecom`（不单列 wecom）。支付宝 `alipay_mini` 与登录域复用同一份凭证；飞书一份 appId/secret 覆盖全部消息类型，故单用 `feishu`。编码口径见框架契约 C5。

## 四、文件 API（FileGateway）

文件域平台实现与 `FileStorageService` 构建全量委托 x-file-storage 原始包（`x-file-storage-core`，非其 starter），门面只做配置装载 + 出口委托，返回官方原始类型，不另造结果对象、不重做对方能力。`<channelCode>` 即 x-file-storage 平台类型段名（`local`/`aliyun-oss`/`minio`…，官方 kebab-case 与 camelCase 均支持，装载层统一归一化），与其余域同为类型编码；多实例用不同 `configId` 区分。

### 4.1 方法清单

| 方法 | 参数 | 返回 | 说明 |
|---|---|---|---|
| `upload` | `String configId, String channelCode, Object source` | `FileInfo` | 上传，委托 `service.of(source).setPlatform(channelCode).upload()` |
| `upload` | `String configId, String channelCode, Object source, String name` | `FileInfo` | 指定原始文件名的上传（byte[]/InputStream/URL 等） |
| `download` | `String configId, String channelCode, FileInfo fileInfo` | `Downloader` | 下载，返回官方 Downloader（`bytes()`/`file()`/`outputStream()`） |
| `delete` | `String configId, String channelCode, FileInfo fileInfo` | `boolean` | 删除 |
| `exists` | `String configId, String channelCode, FileInfo fileInfo` | `boolean` | 存在性判断 |
| `generatePresignedUrl` | `String configId, String channelCode, FileInfo fileInfo, Date expiration` | `String` | 生成预签名 URL（放开 B） |
| `copy` | `String configId, String channelCode, FileInfo fileInfo` | `CopyPretreatment` | 复制，返回官方预处理器供链式（放开 C） |
| `move` | `String configId, String channelCode, FileInfo fileInfo` | `MovePretreatment` | 移动，返回官方预处理器供链式（放开 C） |
| `nativeService` | `String configId, String channelCode` | `FileStorageService` | 原生出口：分片/ACL/元数据/列举/图片处理等进阶能力直接用官方 API |
| `registerChannel` | `FileChannelProvider` | `void` | 增补自定义平台声明 |
| `registeredChannels` | — | `Set<String>` | 已声明渠道编码集合 |

`FileGateway.Builder` 方法：`.configProvider(FileConfigProvider)`（**必填**，与其他域一致；业务链路每次读配置）、`.channel(FileChannelProvider)`（仅声明自定义平台，可多次调用；内置平台由配置驱动构建）、`.build()`（缺 configProvider 抛 `CONFIG_MISSING`，并按 configId 聚合全部配置预热装载）。

### 4.2 配置与 FileRecorder 边界

| 类型 | 说明 |
|---|---|
| `FileChannelConfig` | 自有扁平 bean：`configId` `channelCode`（平台类型） `attr`（平台凭证，不含官方类） |
| 配置源 SPI | `FileConfigProvider.getConfig(configId, channelCode)`，查不到抛 `GatewayException(CONFIG_MISSING)`；内置 `InMemoryFileConfigProvider`（纯 Java/测试）、starter 的 `PropertiesFileConfigProvider`（读 `conflux.file.configs.<configId>.<channelCode>`） |

平台配置 value 是自有扁平 `attr`，装载层按 channelCode 反射官方 Config 类、`convertValue(attr)` 映射后交官方 builder，**不硬编码平台清单**；同一类型多实例用不同 `configId`。文件域**不实现、不托管** `FileRecorder`、不落库：门面一律以 `FileInfo` 为入参/返回，不暴露 `getFileInfoByUrl` 及一切 `url → 元信息` 反查方法（这些方法依赖 DB 记录，官方默认 `DefaultFileRecorder` 下抛明确异常）。需要按 url 查询请自实现 `FileRecorder`，再走 `nativeService()` 原生能力。

## 五、登录 API（AuthGateway）

登录域门面只做路由，业务方法为「生成授权入口」+「回调换身份」两步；24 种形态（微信/支付宝/钉钉/飞书）共用同一套 API，差异在 `channelCode` 与配置。

### 5.1 方法清单

| 方法 | 参数 | 返回 | 说明 |
|---|---|---|---|
| `authorize` | `String configId, String channelCode` | `AuthorizeResult` | 生成授权入口（网页 URL / 扫码二维码 / 小程序 JSAPI 参数） |
| `authorize` | `String configId, String channelCode, AuthorizeOption option` | `AuthorizeResult` | 带平台特有参数、state 透传上下文、过期秒数覆盖 |
| `parseCallback` | `String configId, String channelCode, AuthCallbackRequest request` | `AuthResult<AuthUser>` | 回调换身份：票据交换 + 身份归一，门面只转发 |
| `registerChannel` | `AuthChannel` | `void` | 增补自定义渠道（同编码覆盖内置） |
| `registeredChannels` | — | `Set<String>` | 已注册渠道编码集合（含 24 个内置形态） |

`AuthGateway.Builder` 方法：`.configProvider(AuthConfigProvider)`（**必填**，缺则抛 `CONFIG_MISSING`）、`.stateCache(StateCache)`（可选，默认内存实现）、`.channel(AuthChannel)`（仅自定义渠道）、`.build()`（经 SPI 自动注册 24 个内置形态）。

### 5.2 请求/结果与配置

| 类型 | 关键字段 |
|---|---|
| `AuthorizeResult` | `authorizeUrl` / `qrCodeUrl` / `jsApiParams`（按形态填充，调用方按字段有无判断） |
| `AuthCallbackRequest` | `params`（原始参数全量）、`headers` / `httpMethod` / `rawBody`（验签用）；`param(name)` / `firstParam(names...)` 按名取值 |
| `AuthResult<AuthUser>` | `success` / `errorCode`（字符串）/ `errorMsg` / `data` |
| `AuthUser` | `openId` / `unionId` / `channelCode` / `nickName` / `avatar` 等（各平台归一，小程序等形态部分字段可空） |
| `AuthConfig` | `configId` / `channelCode` / `clientId` / `clientSecret` / `redirectUri` / `scope`（一等字段）+ `attr`（平台特有键） |
| 配置源 SPI | `AuthConfigProvider.getConfig(configId, channelCode)`，查不到抛 `GatewayException(CONFIG_MISSING)`；内存 `InMemoryAuthConfigProvider`、starter 的 `PropertiesAuthConfigProvider`（读 `conflux.auth.configs.<configId>.<channelCode>`） |

门面不验签、不判重、不落库；state 链路（生成/写缓存/取出即删）在 `AbstractAuthChannel` 闭环，票据交换与验签由各渠道委托底层 SDK / 标准协议完成。错误码：`AuthErrorCode`（6xxx 段，框架级抛异常）；渠道侧失败以字符串码（如 `WECHAT_OAUTH_ERROR`、`ILLEGAL_STATE`）在 `AuthResult.errorCode` 表达。

## 六、构建器（纯 jar 场景）

| 门面 Builder 方法 | 支付 | 发票 | 短信 | 文件 | 登录 | 说明 |
|---|---|---|---|---|---|---|
| `.channel(Provider)` | ✓ | ✓ | ✓ | ✓ | ✓ | **仅自定义渠道扩展口**，可多次调用，同编码覆盖内置；内置渠道由 `build()` 自动注册、用户零调用；短信传 `SmsChannelProvider`、文件传 `FileChannelProvider`（第三方原生实例）、登录传 `AuthChannel` |
| `.configProvider(...)` | ✓ 必填 | ✓ 必填 | ✓ 必填 | ✓ 必填 | ✓ 必填 | 配置提供者，必填、每次调用读取 |
| `.clock(Clock)` | ✓ | ✓ | — | — | — | 注入时钟（测试用） |
| `.fileOptions(InvoiceFileOptions)` | — | ✓ | — | — | — | 版式文件固化选项 |
| `.stateCache(StateCache)` | — | — | — | — | ✓ | 登录域专属：state 缓存 SPI（默认内存，集群可注入分布式实现） |
| `.build()` | ✓ | ✓ | ✓ | ✓ | ✓ | 构建网关 |

## 七、相关文档

- **五域框架统一契约（图纸/规范/检查清单）**：[framework-contract.md](../framework-contract.md) —— 新增能力或渠道必须严格执行
- 使用指南：[支付](../guides/pay.md) · [发票](../guides/invoice.md) · [短信](../guides/notify.md) · [文件](../guides/file.md) · [登录](../guides/auth.md)
- 配置键：[config-keys.md](config-keys.md)（支付）、[invoice-platforms.md](invoice-platforms.md)（发票）
- 平台核对与协议细节：[invoice-platforms.md](invoice-platforms.md)
