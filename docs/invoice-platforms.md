# 数电发票平台接入与文档核对清单

本文记录 `conflux-invoice-channels` 第一版渠道的**公开 API 文档核对状态**与接入方式。

> **原则**：框架不编造未经核实的接口 URL 与字段名。渠道规格 `RestChannelSpec` 上的
> `documentVerified` 标记文档是否已逐项核对；未核对的渠道默认不带任何接口路径，
> 对应能力不会暴露，使用前必须按平台最新公开文档配置路径与字段映射。
>
> **例外**：百旺金穗云协议特殊，采用专属适配器（见第 3 节），自带已核对的默认路径与签名实现，
> 不再走「先配路径再启用」的通用骨架流程；其余渠道仍按上述规则。

## 1. 平台清单

| 渠道编码 | 平台 | 通道级别 | 文档核对 | 当前可用能力 |
|---|---|---|---|---|
| `mock` | 内置模拟 | SERVICE_API | 不适用 | 全部（蓝字、授权、异步查单、XML 原件、红字两段、回调） |
| `qixiangyun` | 企享云 | SERVICE_API | 蓝字开具**已核对**；其余待核对 | 蓝字开票（issue）、回调解析 |
| `baiwangjs` | 百旺金穗云 | SERVICE_API | **已核对（语雀官方公开文档）** | 专属适配器：蓝字 V2/V1、查单、红字两段、登录/实人认证、版式回调、XML 法定原件 |
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

## 3. 百旺金穗云：专属适配器（以语雀官方文档为准）

> **勘误**：早期抓取到的 `POST /swgx-saas/swgx-api/interface/jxgl/creditSubmit`
> 是「2.2.5.1 进项发票入账提交接口」，属于同一空间的**进项**分册，并非销项蓝字开具。
> 据此得出「百旺没有公开开票接口」的结论是错误的。用户指定的语雀空间
> `baiwangqiyuanyuan/swgx` 是「税务共享**销项**」官方公开文档（与
> `baiwangjs.apifox.cn` 为同一套「百旺金穗云-SaaS 标准化接口」规范的两个发布渠道），
> 蓝字开具、查单、红冲、登录、实人认证、版式回调均有完整接口定义。
> 本节结论以该文档为准；`RestSpecs.baiwangjs()` 的 `documentVerified` 已置为 `true`
> （仅作元数据登记，实际能力由专属适配器提供，不经过通用 `RestChannelSpec` 驱动）。

百旺协议与通用 REST 适配器差异过大，因此**不复用** `AbstractRestInvoiceChannel`，
由 `baiwangjs` 包下的 `BaiwangJsChannelProvider` 独立实现全部能力，签名/编解码/回调解析封闭在 `support` 子包。

### 3.1 传输与网关签名（语雀《1.接口概述》）

| 项 | 约定 |
|---|---|
| 请求体 | JSON 经 `Base64(UTF-8)` 转码后的**字符串**，`Content-Type: application/json` |
| 同步响应 | 明文 JSON（`code` / `message` / `renson`） |
| 异步回调 | `Content-Type: text/plain`，body 为 Base64 明文 JSON |
| 网关签名 | `Base64(HmacSHA256(sign_str, SECRET))`，**不是 hex** |
| `sign_str` | 固定 5 行、换行分隔、冒号后有空格，顺序不可调整 |
| `Date` | GMT（RFC 1123），日期位两位，如 `Mon, 08 Jan 2024 07:48:16 GMT` |
| `request-line` | 完整 URL 的 path（含网关前缀），如 `/swgx-saas/.../cloud/v2/applyInvoice` |
| `Authorization` | `hmac username="KEY", algorithm="hmac-sha256", headers="request-line date host x-htjs-nonce x-htjs-ua", signature="..."` |

```
POST {request-line} HTTP/1.1
date: {Date}
host: {Host}
x-htjs-nonce: {UUID}
x-htjs-ua: {UserAgent}
```

网关前缀 `/swgx-saas/agentinvoiceservice-cloudservice-cloudservice/agentiscloud`，
默认网关 `https://api.baiwangjs.com`。私有化部署改 `attrs.baseUrl` 即可，`Host` 头与签名 host 会取同一个值。
> 文档示例的 Date 写的是 `Thu, 08 Jan 2024 ...`，但 2024-01-08 实际是**周一**；
> 该字面值只用于复现文档签名串与 HMAC 向量，真实请求由 `gmtDate()` 按日历生成正确星期。

### 3.2 接口路径清单（同一能力 V2 优先、V1 保留实现）

| 能力 | attrs 覆盖键 | V2（默认） | V1（`apiVersion=V1`） |
|---|---|---|---|
| 蓝字开具申请 3.2.6 | `issuePath` | `/cloud/v2/applyInvoice` | `/cloud/applyInvoice` |
| 按订单流水号查询 3.2.7 | `queryPath` | `/cloud/v2/queryOrder` | `/cloud/queryOrder` |
| 查询企业唯一标识 3.1.1 | — | `/cloud/queryUniqueSign` | 同左（单一版本） |
| 数电红冲申请 3.3.3 | `redApplyPath` | `/cloud/applyElecRedInvoice` | 同左 |
| 数电红冲结果查询 3.3.4 | `redQueryPath` | `/cloud/queryRedInvRes` | 同左 |
| 电子税务局登录 3.4.2 | `authStartPath` / `authSubmitPath` | `/cloud/dzswjLogin` | 同左 |
| 实人认证二维码（同步）3.4.4 | `qrCodePath` | `/elecEwm/queryEwmrzSnyc` | 同左 |
| 认证结果 / 风险查询 3.4.5 | `riskInfoPath` | `/elecEwm/queryRiskInfo` | 同左 |

V1/V2 **不只是路径不同，报文结构也不同**（`BaiwangJsCodec`）：

| 字段 | V1 | V2 |
|---|---|---|
| 销方地址 / 电话 | `xsfDzdh`（`~~` 拼接） | `xsfDz` + `xsfDh` |
| 销方银行 / 账号 | `xsfYhzh`（`~~` 拼接） | `xsfYh` + `xsfZh` |
| 购方地址 / 电话 | `gmfDzdh` | `gmfDz` + `gmfDh` |
| 购方银行 / 账号 | `gmfYhzh` | `gmfYh` + `gmfZh` |
| 明细序号 | 带 `xh` | 不带 `xh` |

`attrs.apiVersion=V1` 时开票与查单**同时**切到 V1 路径与 V1 报文；空值 / 未知值回落 V2，
不会因拼写错误静默切到老接口。所有路径也都可用上表覆盖键单独改写，便于平台升级或私有化部署，无需改代码。

### 3.3 异步回调：票号与版式分离

平台把「开票结果」与「版式文件地址」拆成**两次独立回调**，与框架「`SUCCEEDED` 前必须归档 XML 法定原件」冲突。
为此编排层（`InvoiceOrchestrator`）新增「**补充型回调**」语义：只合并票面字段 / 归档文件、**不迁移状态**，
两类回调先后顺序不定也能收敛到同一单据，且按 CAS 合并避免先到的票号被后到的版式回调抹掉。

| `ywlxdm` | 含义 | 框架处理 |
|---|---|---|
| `fpkj` | 开票结果（含红字开具结果，红字带 `yqdfphm` 原票号） | `status=1` 只补票号/日期/金额，**不置 SUCCEEDED**，等版式回调；`2`→FAILED、`3`→PROCESSING、`6`→CLOSED |
| `qdbswj` | 版式文件地址（`xmlurl` / `ofdurl` / `pdf` / `jfdz`） | `status=1` 归档 XML/OFD/PDF 并置 `SUCCEEDED`；失败不推进成功 |
| `qdhzsq` | 红字确认单申请结果 | `WAITING_CONFIRM` / `FAILED`，携带 `hzfpxxqrdbh` |
| `ewmrz` | 实人认证二维码 | 登记 `rzid` / `ewm` 到 attrs，不改状态 |
| `cxnsrfx` | 认证结果 / 风险信息 | 登记 `sfsl` / `sxlb` / `yjjb` 到 attrs，不改状态 |
| `1004` / `1003` | 需要登录 / 需要实人认证 | 映射 `WAITING_AUTH` 挂起态并触发授权会话 |

回调应答固定 `{"code":"0","message":"成功"}`；失败也返回 `code=1` 的 JSON，避免平台收不到应答。

**验签**：平台公开文档未定义回调签名头 / 参数，故**不伪造验签规则**；账户定位依赖回调 URL 上的 `?accountKey=...`。
生产环境应通过网络层白名单 / 网关侧校验保障；若宿主另行约定了签名，可配 `attrs.callbackSignKey` + 查询参数 `sign` 启用 HMAC 校验。

### 3.4 配置项（`conflux.invoice.apps.<appId>.channels.baiwangjs`）

| 键 | 位置 | 说明 |
|---|---|---|
| `seller-tax-no` | 顶层 | 销方税号，路由键与账户键第二段 |
| `notify-url` | 顶层 | 回调地址，须带 `?accountKey=<channelAppId>#<sellerTaxNo>`（`#` 转义为 `%23`） |
| `tier` / `priority` / `enabled` | 顶层 | 通道分级与路由权重 |
| `gatewayKey` / `gatewaySecret` | attrs | 网关 KEY / SECRET，HMAC 签名用（**不是**业务 appKey） |
| `qyId` | attrs | 企业唯一标识，由 3.1.1 查询一次后配置，必填 |
| `sellerTaxNo` / `nsrsbh` | attrs | 写入报文的销方税号 |
| `dlzh` / `kpr` / `sqr` | attrs | 电局登录账号（开票员手机号）/ 开票人 / 申请人 |
| `sellerName`、`sellerAddress`、`sellerPhone`、`sellerBank`、`sellerBankAccount` | attrs | 销方票面信息；V1 合并、V2 拆分 |
| `apiVersion` | attrs | `V2`（默认）/ `V1` |
| `baseUrl` / `userAgent` / `timeoutMillis` | attrs | 网关地址（私有化部署）、UA、超时 |
| `issuePath` 等路径键 | attrs | 见 3.2 表 |
| `fplxdm.<InvoiceType>` | attrs | 票种编码覆盖（内置数电专票 `01` / 普票 `02` 等默认值） |
| `callbackSignKey` | attrs | 可选，启用回调 HMAC 验签 |

完整配置模板（含全部可覆盖键，默认注释、不影响零凭据启动）见
`conflux-invoice-example/src/main/resources/application.yml`。

### 3.5 如实标注的边界

- 上述路径、字段、状态码、签名规则均取自**公开文档**并做了单测（32 个用例），
  其中含 2 个编排层端到端用例，验证 `fpkj` / `qdbswj` 两种到达顺序下票号与 XML 原件收敛到同一单据；
  **尚未与平台做真实联调**；首次接入须用沙箱 / 测试企业核对 `qyId`、`fplxdm` 字典与回调地址可达性。
- 销项侧**没有独立的版式文件查询接口**：文件地址只由 `qdbswj` 回调给出，
  `InvoiceFileFetcher.fetch()` 只做已记录 URL 的归一化，不编造下载接口。
- 公开文档未给出「红字确认单确认后单独开具」接口，红字开具由平台在确认后自动完成；
  `issueRed()` 返回异步受理并等待 `fpkj`（带 `yqdfphm`）回调，不重复调用申请接口。
- 3.1.1 查询 `qyId` 的接口已登记在 `BaiwangJsSpec`，但框架不自动调用，由使用方查询后配置，避免启动期产生外部依赖。

百旺票税云、金蝶发票云仍为骨架，待按各自公开文档核对。

## 4. 通用 REST 适配器的配置键

> 本节仅适用于复用 `AbstractRestInvoiceChannel` 的渠道；百旺金穗云的配置键见 3.4 节。

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
2. 常规平台新增一个继承 `AbstractRestInvoiceChannel` 的 Provider 类，在 starter 中注册为 Bean；
   协议差异过大时（如百旺金穗云的 Base64 报文 + 网关签名 + 双回调）应像
   `BaiwangJsChannelProvider` 一样直接实现 `InvoiceChannelProvider` 及各能力接口，不强行套通用适配器；
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
