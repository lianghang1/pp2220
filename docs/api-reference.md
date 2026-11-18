# API 参考

统一入口是两个门面类，双形态（Spring Boot 注入 / 纯 jar `builder()`）方法完全相同。全部请求/结果类位于 `*.api` 包，状态枚举位于 `*.model` 包，链式 setter（`@Accessors(chain=true)`）。

| 门面 | 全限定名 | 获取方式 |
|---|---|---|
| 支付 | `io.github.lianghang1.conflux.pay.engine.PaymentGateway` | Spring 注入，或 `PaymentGateway.builder()....build()` |
| 发票 | `io.github.lianghang1.conflux.invoice.engine.InvoiceGateway` | Spring 注入，或 `InvoiceGateway.builder()....build()` |

金额单位：支付 = 分（`Long`），发票 = 元（`BigDecimal`）。

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
| `parseCallback` | `ChannelCallbackRequest` | `CallbackEvent` | 验签并归一回调事件 |
| `callbackSuccessReply` | `String channelCode` | `String` | 处理成功应答报文 |
| `callbackFailureReply` | `String channelCode` | `String` | 处理失败应答报文（触发渠道重推） |
| `fetchBill` | `String channelCode, String appId, LocalDate billDate` | `BillSheet` | 拉取渠道账单，对账比对由使用方完成 |
| `registerChannel` | `ChannelProvider` | `void` | 启动期注册渠道提供者 |
| `registeredChannels` | — | `Set<String>` | 已注册渠道编码集合 |
| `builder` | — | `Builder` | 创建构建器 |

### 1.2 请求对象字段

| 类 | 字段 |
|---|---|
| `PaymentRequest` | `appId` `bizOrderNo` `method` `channelCode` `amount` `currency` `subject` `body` `clientIp` `channelExtras` |
| `PaymentQueryRequest` | `appId` `bizOrderNo` `channelCode` `channelOrderNo` |
| `RefundRequest` | `appId` `bizRefundNo` `channelCode` `bizOrderNo` `channelOrderNo` `totalAmount` `amount` `currency` `reason` `channelExtras` |
| `RefundQueryRequest` | `appId` `bizRefundNo` `channelCode` `channelRefundNo` `bizOrderNo` |
| `TransferRequest` | `appId` `bizTransferNo` `method` `payeeAccount` `payeeName` `amount` `currency` `remark` `channelExtras` |
| `TransferQueryRequest` | `appId` `bizTransferNo` `channelCode` `channelTransferNo` |
| `ProfitShareRequest` | `appId` `bizShareNo` `channelCode` `bizOrderNo` `channelOrderNo` `receivers`（`List<ProfitShareReceiver>`）`channelExtras` |
| `ProfitShareQueryRequest` | `appId` `bizShareNo` `channelCode` `channelShareNo` |
| `ChannelCallbackRequest` | `channelCode` `headers` `queryParams` `formParams`（均 `Map<String,String>`）`body` |

### 1.3 结果对象字段

| 类 | 字段 |
|---|---|
| `PaymentResult` | `appId` `bizOrderNo` `method` `channelCode` `channelOrderNo` `state` `payPayload` `errorCode` `errorMsg` `succeedTime` |
| `RefundResult` | `appId` `bizRefundNo` `amount` `currency` `state` `channelRefundNo` `errorCode` `errorMsg` `succeedTime` |
| `TransferResult` | `appId` `bizTransferNo` `method` `channelCode` `state` `channelTransferNo` `errorCode` `errorMsg` `succeedTime` |
| `ProfitShareResult` | `appId` `bizShareNo` `amount` `receivers` `state` `channelShareNo` `errorCode` `errorMsg` `finishTime` |
| `CallbackEvent` | `type`（`BizOrderType`）`appId` `bizOrderNo` `channelOrderNo` `success` `amount` `occurTime` `raw` |
| `BillSheet` | `appId` `channelCode` `billDate` `entries`（`List<BillEntry>`） |

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
| `parseCallback` | `InvoiceCallbackRequest` | `InvoiceCallbackEvent` | 验签并归一回调事件 |
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
| `InvoiceRequest` | `appId` `bizOrderNo` `channelCode` `sellerTaxNo` `sellerName` `buyerTaxNo` `buyerTitle` `buyerAddress` `buyerBank` `buyerPhone` `buyerMobile` `buyerEmail` `invoiceType` `items`（`List<InvoiceItem>`）`amount` `taxAmount` `remark` `drawer` `tspz` `zsfs` `jzfwTdys` `bdcTdysList` `notifyUrl` `sandbox` `attrs` |
| `InvoiceQueryRequest` | `appId` `channelCode` `bizOrderNo` `channelOrderNo` `invoiceNo` `invoiceCode` `invoice` |
| `AuthSubmission` | `appId` `captcha` `token` `extra` `session`（`AuthSession`）`invoice` |
| `RedApplyRequest` | `appId` `bizRedNo` `reason` `chyydm` `amount` `channelCode` `notifyUrl` `originalInvoice` |
| `RedApplyQueryRequest` | `appId` `redApply` |
| `RedConfirmRequest` | `appId` `accept` `reason` `extra` `redApply` |
| `RedIssueRequest` | `appId` `channelCode` `notifyUrl` `redApply` `originalInvoice` |
| `DeliveryRequest` | `appId` `phone` `email` `extra` `invoice` |
| `RepairRequest` | `appId` `channelCode` `sellerTaxNo` `invoiceId` `invoiceCode` `invoiceNo` `startDate` `endDate` `invoiceTypeCode` `channelOrderNo` `extra` |
| `InvoiceCallbackRequest` | `channelCode` `queryParams` `formParams` `headers`（均 `Map<String,String>`）`body` |

### 2.3 结果对象字段

| 类 | 字段 |
|---|---|
| `InvoiceResult` | `invoiceId` `bizOrderNo` `state` `channelCode` `channelInstanceId` `channelOrderNo` `invoiceNo` `invoiceCode` `invoiceDate` `invoiceTime` `amount` `taxAmount` `files` `authChallenge` `asyncAccepted` `errorCode` `errorMsg` `rawPayload` `invoice` `authSession` |
| `QuickRedResult` | `outcome`（`ACCEPTED` / `WAITING_CONFIRM`）`redApply` `redInvoice` |
| `RedApply` | `redApplyId` `appId` `bizRedNo` `originalInvoiceId/No/Code` `reason` `chyydm` `amount` `channelCode` `channelInstanceId` `accountKey` `channelOrderNo` `redConfirmNo` `state` `confirmDeadline` `confirmedTime` `redInvoiceId` `notifyUrl` `errorCode` `errorMsg` |
| `DeliveryResult` | `accepted` `asyncAccepted` `channelOrderNo` `errorCode` `errorMsg` `rawPayload` |
| `RepairResult` | `channelOrderNo` `files` `invoiceNo` `invoiceCode` `errorCode` `errorMsg` `rawPayload` |
| `AuthSession` | `sessionId` `invoiceId` `appId` `channelCode` `accountKey` `authType` `state` `challengeUrl` `challengeText` `expireTime` `submittedTime` `payload` |
| `InvoiceCallbackEvent` | `accountKey` `invoiceId` `redApplyId` `channelOrderNo` `bizOrderNo` `state` `invoiceNo` `invoiceCode` `invoiceDate` `amount` `taxAmount` `files` `redState` `redConfirmNo` `errorCode` `errorMsg` `rawPayload` `attrs` |
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

## 三、构建器（纯 jar 场景）

| 门面 Builder 方法 | 说明 |
|---|---|
| `.channel(Provider)` | 注册渠道，可多次调用 |
| `.configProvider(...)` | 配置提供者（必填） |
| `.clock(Clock)` | 注入时钟（测试用） |
| `.fileOptions(InvoiceFileOptions)` | 版式文件固化选项（仅发票） |
| `.build()` | 构建网关 |

## 四、相关文档

- 配置键：[config-keys.md](config-keys.md)（支付）、[invoice-platforms.md](invoice-platforms.md) §3.4/§4.5/§6（发票）
- 接入流程：[pay-integration.md](pay-integration.md)
- 平台核对与协议细节：[invoice-platforms.md](invoice-platforms.md)
