# 数电发票平台接入与文档核对清单

本文记录 `conflux-invoice-channels` 第一版渠道的**公开 API 文档核对状态**与接入方式。

> **原则**：框架不编造未经核实的接口 URL 与字段名。渠道规格 `RestChannelSpec` 上的
> `documentVerified` 标记文档是否已逐项核对；未核对的渠道默认不带任何接口路径，
> 对应能力不会暴露，使用前必须按平台最新公开文档配置路径与字段映射。

## 1. 平台清单

| 渠道编码 | 平台 | 通道级别 | 文档核对 | 当前可用能力 |
|---|---|---|---|---|
| `mock` | 内置模拟 | SERVICE_API | 不适用 | 全部（蓝字、授权、异步查单、XML 原件、红字两段、回调） |
| `qixiangyun` | 企享云 | SERVICE_API | 蓝字开具**已核对**；其余待核对 | 蓝字开票（issue）、回调解析 |
| `baiwangjs` | 百旺金穗云 | SERVICE_API | **待核对** | 骨架，配置路径后启用 |
| `baiwang` | 百旺票税云 | SERVICE_API | **待核对** | 骨架，配置路径后启用 |
| `kingdee` | 金蝶发票云 | SERVICE_API | **待核对** | 骨架，配置路径后启用 |

**通道分级**说明：

| 级别 | 说明 | 路由权重 |
|---|---|---|
| `DIRECT_TAX` | 乐企 / 税局直连，能力最完整，需企业准入与税局备案 | 300 |
| `SERVICE_API` | 服务商开放 API，公开文档、可自动化，能力受平台封装限制 | 200 |
| `WEB_PORTAL` | 电局网页 / 模拟 RPA，依赖登录态、扫脸、短信，作为兜底 | 100 |

同一 `appId + sellerTaxNo` 下可注册多个实例，路由按 `ChannelTier.priority × 1000 + ChannelInstance.priority`
择优；显式指定 `channelCode` 时只在该渠道内选择（灰度 / 降级用）。

## 2. 企享云：已核对的蓝字发票开具接口

```
POST https://api.qixiangyun.com/v2/invoice/qdfp/fpkjZzs
Content-Type: application/json
```

公开文档示例中的请求字段（框架在 `AbstractRestInvoiceChannel.issue` 中据此组装）：

| 字段 | 含义 | 框架来源 |
|---|---|---|
| `action` | 接口动作，`fpkj_zzs` | `RestChannelSpec.actionCode` |
| `async` | 是否允许转异步 | `Invoice.asyncAllowed` |
| `nsrsbh` | 销方纳税人识别号 | `Invoice.sellerTaxNo` |
| `ly_ddbh` | 来源订单号（幂等键） | `Invoice.bizOrderNo` |
| `jshj` | 价税合计 | `Invoice.amount` |
| `hjse` | 合计税额 | `Invoice.taxAmount` |
| `wjgs` | 文件格局（框架固定 `xml`） | — |
| `fplxdm` | 发票类型代码 | `attrs` 中 `fplxdm.<InvoiceType>` 映射，缺省用枚举名 |
| `xmmx` | 明细行数组 | `Invoice.items` |
| `data.xsf_yhmc` | 销方信息 | `Invoice.sellerName` |
| `data.gmf_mc` / `gmf_nsrsbh` | 购方名称 / 税号 | `Invoice.buyerTitle` / `buyerTaxNo` |

响应字段：`reqId`、`code`、`success`、`message`、`data.fphm`（发票号）。
`code=2001` 表示**超过 40 秒转异步**，框架据此返回 `PROCESSING`，后续由查单或回调推进。

> 字段名的语义与必填性、票种编码字典、签名规则须以平台最新公开文档为准，
> 框架只按上表做最小可用映射，不声称覆盖全部业务场景。

**关键约束**：多数服务商同步开票只返回发票号，`XML` 法定原件需单独下载。
因此框架在渠道返回「开票成功但未带 XML」时不会直接置 `SUCCEEDED`，而是：

1. 若响应内联了 XML，直接归档；
2. 否则返回 `PROCESSING`，等查单 / 文件下载能力补齐；
3. 归档时若仍无 `InvoiceFileType.XML`，抛 `XML_ORIGINAL_REQUIRED` 拒绝置成功。

## 3. 百旺金穗云：文档页需要重新定位

按公开文档抓取到的页面实际是 **「2.2.5.1 进项发票入账提交接口」**：

```
POST https://api.baiwangjs.com/swgx-saas/swgx-api/interface/jxgl/creditSubmit
```

请求字段为 `czlsh`、`nsrsbh`、`rzmxList`——这是**进项入账**，不是蓝字开具。
把它当开票接口使用会产生严重业务错误，因此 `RestSpecs.baiwangjs()` 不带任何接口路径，
`docNote` 中记录了这一事实。启用前需重新定位以下文档页并逐字段核对：

- 蓝字发票开具 / 结果查询
- 红字确认单申请 / 确认状态查询 / 红字发票开具
- 版式文件（XML / OFD / PDF）下载
- 授权会话（如平台要求扫脸 / 短信）
- 回调通知格式与验签规则

百旺票税云、金蝶发票云同理：骨架已注册，字段待按各自公开文档核对。

## 4. 通用 REST 适配器的配置键

渠道路径与票种编码全部可通过 `conflux.invoice.apps.<appId>.channels.<channel>.attrs` 覆盖：

| attrs 键 | 作用 | 对应能力 |
|---|---|---|
| `baseUrl` | 平台网关地址 | 全部 |
| `appKey` / `channelAppId` | 渠道侧应用标识（账户键第一段） | 配置反查 |
| `appSecret` | 签名密钥 | 请求签名 / 回调验签 |
| `sellerTaxNo` / `nsrsbh` | 销方纳税人识别号（账户键第二段） | 路由 / 反查 |
| `timeoutMillis` | 单次 HTTP 超时（默认 30000） | 全部 |
| `issuePath` | 蓝字开票 | `InvoiceIssuer` |
| `queryPath` | 查单 / 结果轮询 | `InvoiceQuerier` |
| `filePath` | 版式文件下载 | `InvoiceFileFetcher` |
| `redApplyPath` | 红字确认单申请 | `InvoiceRedHandler.apply` |
| `redQueryPath` | 红字确认单状态查询 | `InvoiceRedHandler.queryRed` |
| `redIssuePath` | 红字发票开具 | `InvoiceRedHandler.issueRed` |
| `authStartPath` | 发起授权 | `AuthSessionHandler.start` |
| `authSubmitPath` | 提交授权凭证 | `AuthSessionHandler.submit` |
| `fplxdm.<InvoiceType>` | 票种编码映射 | 开票请求 |

能力暴露规则：

- `documentVerified=true` 的渠道（企享云）：只暴露规格中确有路径的能力；
- `documentVerified=false` 的渠道（可配置骨架）：能力先暴露，真正调用时校验路径是否已配置，
  未配置抛 `CHANNEL_CAPABILITY_NOT_SUPPORTED` 并提示补齐，绝不返回伪造的成功。

## 5. 接入新平台的步骤

1. 在 `RestSpecs` 增加一份 `RestChannelSpec`，如实填写 `documentVerified` 与 `docNote`；
2. 新增一个继承 `AbstractRestInvoiceChannel` 的 Provider 类，在 starter 中注册为 Bean；
3. 若平台签名规则不是默认 HMAC-SHA256，覆写 `buildHeaders`；若响应结构与通用解析差异大，
   覆写 `call` 的解析或 `toInvoiceResult`；
4. 在 `attrs` 中配置路径与票种编码映射，用 mock 单测覆盖「成功 / 异步 / 授权 / 失败 / 无 XML」分支；
5. 按平台文档核对字段语义后再将 `documentVerified` 置为 `true`。

## 6. 与支付域复用的部分

- `ChannelProvider` SPI + `ChannelRegistry` 按能力查找、缺失报明确异常；
- `AccountKey` 复合键与配置装载期 `putIfAbsent` + `CONFIG_CONFLICT` fail-fast；
- `HttpTransport`（common）统一 OkHttp 传输层，渠道不直接依赖 OkHttp；
- `ErrorCode` 三段分层（1xxx / 2xxx / 3xxx）；
- Store SPI 交由使用方实现、框架零表结构。

发票域额外引入的状态机挂起态、授权会话、通道分级、XML 法定原件、两段红冲、
Store CAS 与租约，均为支付域没有的语义，详见 [README](../README.md)。
