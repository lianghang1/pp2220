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
| `baiwangjs` | 百旺金穗云 | SERVICE_API | **已核对（官方公开文档）** | 专属适配器：蓝字 V2/V1、查单、红字两段（含 3.3.12 确认后开具 / 3.3.15 状态 / 3.3.16 主动确认）、登录与实人认证（同步+异步二维码）、3.4.15 / 3.4.8 版式文件查询、3.4.7 交付重发、3.4.11/12/13 发票修复、3.3.17 可冲销校验、`ywlxdm` 回调分流、XML 法定原件 |
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
| `ly_ddbh` | 来源订单号（渠道商户单号） | `Invoice.bizOrderNo` |
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

## 3. 百旺金穗云：专属适配器（以官方公开文档为准）

> **勘误**：早期抓取到的 `POST /swgx-saas/swgx-api/interface/jxgl/creditSubmit`
> 是「2.2.5.1 进项发票入账提交接口」，属于同一空间的**进项**分册，并非销项蓝字开具。
> 据此得出「百旺没有公开开票接口」的结论是错误的。官方公开的「税务共享**销项**」
> 接口规范（即「百旺金穗云-SaaS 标准化接口」）中，蓝字开具、查单、红冲、登录、
> 实人认证、版式回调均有完整接口定义。
> 本节结论以该规范为准；`RestSpecs.baiwangjs()` 的 `documentVerified` 已置为 `true`
> （仅作元数据登记，实际能力由专属适配器提供，不经过通用 `RestChannelSpec` 驱动）。

百旺协议与通用 REST 适配器差异过大，因此**不复用** `AbstractRestInvoiceChannel`，
由 `baiwangjs` 包下的 `BaiwangJsChannelProvider` 独立实现全部能力，签名/编解码/回调解析封闭在 `support` 子包。

### 3.1 传输与网关签名（《1.接口概述》）

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

下表为按官方公开文档《3.接口定义》逐条核对后**已落地**的路径；加粗项为本轮 A/B 档补齐。
所有路径都可用 `attrs` 中的覆盖键改写，便于平台升级或私有化部署，无需改代码。

| 能力（文档编号） | attrs 覆盖键 | 默认路径 | 版本 / 触发 |
|---|---|---|---|
| 蓝字开具申请 3.2.6 | `issuePath` | `/cloud/v2/applyInvoice` | V2；V1 = `/cloud/applyInvoice` |
| 按订单流水号查询 3.2.7 | `queryPath` | `/cloud/v2/queryOrder` | V2；V1 = `/cloud/queryOrder` |
| **查询企业唯一标识 3.1.1** | `uniqueSignPath` | `/cloud/queryUniqueSign` | 单一版本，配置期工具 |
| 数电红冲申请 3.3.3 | `redApplyPath` | `/cloud/applyElecRedInvoice` | 单一版本 |
| 数电红冲结果查询 3.3.4 | `redQueryPath` | `/cloud/queryRedInvRes` | 单一版本（无确认单号时回退） |
| **数电红冲开具 3.3.12** | `redIssuePath` | `/cloud/doElecRedInvoice` | 确认单 CONFIRMED 后真调 |
| **红字确认单状态查询 3.3.15** | `redStatusPath` | `/cloud/queryHzqrdzt` | 同步，优先数据源 |
| **红字确认单确认或拒绝 3.3.16** | `redConfirmPath` | `/cloud/updateHzqrdState` | `qrlx=Y/N` |
| **可冲销明细查询 3.3.17** | `redAvailablePath` | `/cloud/hzcxmx` | 异步，回调 `hzcx` |
| 电子税务局登录 3.4.4 | `authStartPath` / `authSubmitPath` | `/cloud/dzswjLogin` | 两步短信 |
| 实人认证二维码（同步）3.4.6 | `qrCodePath` | `/elecEwm/queryEwmrzSnyc` | 默认 |
| **实人认证二维码（异步）3.4.5** | `qrCodeAsyncPath` | `/elecEwm/queryEwmrz` | `attrs.qrMode=ASYNC` |
| **认证结果 / 风险查询 3.4.14** | `riskInfoPath` | `/elecEwm/queryRiskInfo` | 异步，回调 `cxnsrfx` |
| **发票短信邮箱重发 3.4.7** | `deliveryPath` | `/cloud/fpReSend` | 同步 |
| **电子发票下载地址查询 3.4.8** | `downloadPath` | `/cloud/invoiceDownload` | 同步（按 ddlsh） |
| **电票版式文件查询 3.4.15** | `layoutQueryPath` | `/cloud/queryBswj` | 同步（按票号） |
| **发票修复 3.4.11 / (V2)** | `repairPath` | `/cloud/v2/invoiceRepair` | V2；V1 = `/cloud/invoiceRepair` |
| **发票修复结果查询 3.4.12** | `repairResultPath` | `/cloud/repairResult` | 同步（默认） |
| **发票查询-按票号或时间 3.4.13** | `queryBatchPath` | `/cloud/queryInvBatch` | 同步，`repairResultMode=BATCH` |

V1/V2 **不只是路径不同，报文结构也不同**（`BaiwangJsCodec`）：

| 字段 | V1 | V2 |
|---|---|---|
| 销方地址 / 电话 | `xsfDzdh`（`~~` 拼接） | `xsfDz` + `xsfDh` |
| 销方银行 / 账号 | `xsfYhzh`（`~~` 拼接） | `xsfYh` + `xsfZh` |
| 购方地址 / 电话 | `gmfDzdh` | `gmfDz` + `gmfDh` |
| 购方银行 / 账号 | `gmfYhzh` | `gmfYh` + `gmfZh` |
| 明细序号 | 带 `xh` | 不带 `xh` |

`attrs.apiVersion=V1` 时开票、查单与发票修复**同时**切到 V1 路径，开票报文同时切到 V1 合并字段；
空值 / 未知值回落 V2，不会因拼写错误静默切到老接口。其余接口文档只有单一版本，
同一能力的同步/异步两种模式走 `attrs` 切换，不为版本或模式各开一个 SPI 方法。

### 3.2.1 特殊票种（tspz）与特定业务要素（已按生产实现与文档双向核对）

`tspz` 是数电票的**特殊票种标志**：`00` 普通票（默认）、`01` 成品油、`03` 建筑服务、
`04` 货物运输服务、`05` 不动产销售、`06` 不动产经营租赁、`09` 旅客运输服务、
`0200` 稀土、`1200` 农产品销售、`1600` 农产品收购、`24` 报废品收购、`51`/`52` 二手车正/反向开具等
（机动车/二手车按平台口径不纳入）。框架在 `Invoice` / `InvoiceRequest` 上新增：

| 框架字段 | 说明 |
|---|---|
| `tspz` / `zsfs` | 特殊票种标志 / 征收方式（0 普通、1 减按计征、2 差额开票、3 差额全额开票、4 减按征收-经营租赁） |
| `jzfwTdys` | 建筑服务特定业务要素（`tdzzsxmbh`/`jzfwfsd`/`jzxmmc`/`kdsbz`/`kqysssxbgglbm`），存在时自动推断 `tspz=03` |
| `bdcTdysList` | 不动产经营租赁特定业务要素列表（`bdcdz`/`zlqqz`/`kdsbz`/`cqzsh`/`dw`），存在时自动推断 `tspz=06`，行数须与商品行数一致 |
| `buyerMobile` / `buyerEmail` | 电票交付（`gmfMobile`/`gmfEmail`）；渠道规则：都传只发邮箱 |
| `InvoiceItem.lslbs` / `yhzcbs` / `zzstsgl` | 零税率标识（0 正常/1 免税/2 不征税/3 普通零税率）/ 优惠政策标识 / 增值税特殊管理；`yhzcbs=1` 时 `zzstsgl` 必填 |

渠道适配器按文档强制：建筑服务(03)明细 `dj`/`sl` 置空，不动产销售/经营租赁(05/06)明细 `ggxh` 置空。
3.3.3 红冲申请对齐文档必填项：`ykprq`（数电格式 `yyyy-MM-dd HH:mm:ss`，取原蓝票开票时间）、
`yfplxdm`、`sfwzzfp=N`、`chyydm`（冲红原因代码：1 开票有误=全额红冲、2 销货退回、3 服务终止、
4 销售折让，可经 `RedApplyRequest.chyydm` 指定）、红字明细 `lzmxxh`（原蓝字明细行号，取 `InvoiceItem.lineNo`）。

### 3.3 异步回调：票号与版式分离

平台把「开票结果」与「版式文件地址」拆成**两次独立回调**。SDK 只做验签 + 归一：
`InvoiceOrchestrator` 对「补充型回调」只归一票面字段 / 文件载体、**不定义状态推进**，两类回调的字段合并、
去重与最终态判定由使用方按自己的单据处理（SDK 无台账、无 CAS、无状态机）。
法定 XML 原件约束体现在归一结果的判定辅助上，是否置为成功由使用方决定。

| `ywlxdm` | 含义 | 框架处理 |
|---|---|---|
| `fpkj` | 开票结果（含红字开具结果，红字带 `yqdfphm` 原票号） | `status=1` 只补票号/日期/金额，**不置 SUCCEEDED**，等版式回调；`2`→FAILED、`3`→PROCESSING、`6`→CLOSED |
| `qdbswj` | 版式文件地址（`xmlurl` / `ofdurl` / `pdf` / `jfdz`） | `status=1` 归档 XML/OFD/PDF 并置 `SUCCEEDED`；失败不推进成功 |
| `qdhzsq` | 红字确认单申请结果 | `WAITING_CONFIRM` / `FAILED`，携带 `hzfpxxqrdbh` |
| `ewmrz` | 实人认证二维码 | 登记 `rzid` / `ewm` 到 attrs，不改状态 |
| `cxnsrfx` | 认证结果 / 风险信息 | 登记 `sfsl` / `sxlb` / `yjjb` 到 attrs，不改状态 |
| **`hzcx`** | 可冲销明细查询结果（3.3.17） | 登记 `hzcxje` / `hzcxse` / `jshj` 到 attrs，**绝不迁移状态**，避免被误当成 `fpkj` |
| `1004` / `1003` | 需要登录 / 需要实人认证 | 映射 `WAITING_AUTH` 挂起态并触发授权会话 |

回调应答固定 `{"code":"0","message":"成功"}`；失败也返回 `code=1` 的 JSON，避免平台收不到应答。

**⚠️ 两套 status 语义，严禁混用**（已核实官方公开文档 3.2.6 / 3.2.7 / 3.3.3 / 3.3.4 / 3.4.5）：

| 场景 | 成功 | 失败 | 其他 |
|---|---|---|---|
| **回调**（`fpkj` / `qdbswj` / `qdhzsq` / `ewmrz`） | `1` | `2` | `3`=已开具未生成PDF、`6`=单据删除、`1004`/`1003` |
| **3.2.7 查单**（queryOrder V1/V2） | `2` | `3` | `1`=开票中、`8`=已开未生成PDF(税控)、`6`=已作废、`0`待审核、`9`已审核、`10`待扫码、`11`已追回 |
| **3.3.4 红冲结果查询** | `4` | `5` | `1`申请中、`2`待确认、`3`已作废、`hzqrxxztDm 01-10` |
| **3.3.15 确认单状态查询** | `04` 双方已确认 | `05`-`10` 作废类 | `01` 无需确认、`02`/`03` 待对方确认 |

代码中回调用 `BaiwangJsSpec.ST_*` 常量，查单用 `QRY_*` 常量（`BaiwangJsCodec.queryInvoiceResult`）。

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
| `qrMode` | attrs | `ASYNC` 时实人认证二维码走 3.4.5 异步路径（默认 3.4.6 同步） |
| `repairResultMode` | attrs | `BATCH` 时修复结果改走 3.4.13 按票号/时间查询（默认 3.4.12） |
| 3.2 表各路径键 | attrs | 见 3.2 表；逐接口覆盖默认路径 |
| `fplxdm.<InvoiceType>` | attrs | 票种编码覆盖（内置数电专票 `01` / 普票 `02` 等默认值） |
| `callbackSignKey` | attrs | 可选，启用回调 HMAC 验签 |

请求级 `extra` 也可覆盖：`queryMode=BATCH`（单次修复结果查询走 3.4.13）、
`elecBz`（数电票标志，默认 `1`）、`pageNo` / `pageSize`（批量查询分页）、
`fpDm` / `fpHm` / `qdfphm`（交付重发的票号）、`fplxdm`（修复票种）、`gmfMc`（修复购方名称）。

完整配置模板（含全部可覆盖键，默认注释、不影响零凭据启动）见
`conflux-invoice-example/src/main/resources/application.yml`。

### 3.5 骨架层 A/B 档扩展与如实标注的边界

按「通用渠道平台」视角，只把**填实已有骨架能力**与**跨渠道一等可选能力**纳入实现；
商品管理、机动车/二手车、乐企跨区、订单码、打印、税控红字信息表等纯业务 / 渠道专属接口不进骨架。
**数电票没有作废、只有红冲**：作废类接口全部不纳入，`InvoiceState` 状态语义不因此改动。

**A 档（填实已有能力，不改 SPI 签名）**：

| 编码 | 落地 |
|---|---|
| BW-01 / 3.1.1 | `queryUniqueSign(credentials, name, taxNo)` 配置期工具，需显式传网关凭据 |
| BW-30 / 3.3.12 | `issueRed()` 真调确认后开具，不再只查结果返回受理 |
| BW-33 / 3.3.15 | `queryRed()` 优先查确认单状态，无确认单号回退 3.3.4 |
| BW-34 / 3.3.16 | 主动确认/拒绝走新增 `InvoiceRedHandler.confirm(ctx, req)` 可选方法 |
| BW-40 / 3.4.5 | 异步二维码走 `attrs.qrMode=ASYNC`，与同步版共用同一能力 |
| BW-43 / BW-52 / 3.4.8 / 3.4.15 | `fetch()` 真查版式文件；失败一律降级到 `qdbswj` 回调已记录的 URL，绝不阻断主流程 |
| BW-51 / 3.4.14 | `submit()` 扫脸分支接认证结果查询，`sfsl=N` 直接通过 |
| BW-50 / 3.4.13 | 骨架层：`InvoiceQueryRequest` 加 `invoiceNo/invoiceCode`，由请求直接携带票号查询（SDK 无台账，不依赖 Store 反查） |

**B 档（新增可选能力，一律 `default null`）**：

| 编码 | 新增 | 配套 |
|---|---|---|
| BW-42 / 3.4.7 | `InvoiceDeliverer` + `Provider.deliverer()` | `Registry`、`Gateway.resend()`、编排 |
| BW-47/48/49 / 3.4.11/12 | `InvoiceRepairer`（apply + query）+ `Provider.repairer()` | `Gateway.repair()` / `queryRepair()` |
| BW-50 / 3.4.13 | 3.4.11 文档明确修复结果也可由 3.4.13 查询 | `repairResultMode=BATCH` 走 `queryBatchPath` |
| BW-35 / 3.3.17 | `InvoiceRedHandler.checkRedAvailable()` | 返回 null / 异步受理 / 可冲时放行，仅明确「不可冲」抛 `RED_NOT_AVAILABLE` |

门面/编排层新增 `resend()` / `repair()` / `queryRepair()` / `confirmRed(RedConfirmRequest)`；
渠道未实现的可选能力由 `Registry` 统一抛 `CHANNEL_CAPABILITY_NOT_SUPPORTED`，
`mock` / 企享云等渠道不受影响；`hzcx` 回调只归一 attrs、不涉及状态推进（SDK 无状态机）。

**如实标注的边界**：

- 上述路径、字段、状态码、签名规则均取自**公开文档**并做了协议层 / 编排层单测
  （百旺相关 62 个用例），**尚未与平台做真实联调**；首次接入须用沙箱 / 测试企业核对
  `qyId`、`fplxdm` 字典、回调地址可达性与异步回调字段。
- 版式文件以 3.4.15（按票号）/ 3.4.8（按 ddlsh）同步查询为主，`qdbswj` 回调 URL 为降级兜底；
  编排层在置 `SUCCEEDED` 前仍强校验 XML 法定原件，缺则抛 `XML_ORIGINAL_REQUIRED`。
- 3.4.11 修复是**异步**且文档明确「不支持结果回调」：申请只返回受理，结果由
  3.4.12（默认）或 3.4.13（`repairResultMode=BATCH`）查询；`code=-1` 视为「尚未修复到发票」，
  保持 `PROCESSING` 允许继续查询，不误判为终态失败。
- 3.1.1 查询 `qyId` 由使用方在配置期调用一次后写入 `attrs.qyId`，框架**不在启动期**自动发起外部请求。
- 不编造未纳入的接口：作废类（3.3.1 / 3.3.2 / 3.3.10 / 3.3.11）、税控红字信息表、
  商品管理、机动车/二手车、乐企跨区、订单码、打印、智能抬头等按口径留在骨架之外。

百旺票税云、金蝶发票云仍为骨架，待按各自公开文档核对。

## 4. 票通云：专属适配器（以《票通数电发票接口文档 3.2.0》为准）

> 与生产环境在用代码逐字段交叉验证过（签名串、3DES、流水号规则、状态归一）。
> 接口核对清单见仓库外 `piaotong-api-codes.md`（PT-01 ~ PT-51，A 档 15 + B 档 9 已实现）。

### 4.1 传输与签名（文档 1.1/1.2，与百旺体系完全不同）

| 项 | 票通实现 |
|---|---|
| 传输 | POST JSON **明文信封**（`platformCode/signType/format/version/content/timestamp/serialNo/sign`） |
| 业务报文 | `content` = `Base64(3DES-ECB(password, JSON))`，UTF-8 |
| 签名 | RSA `SHA1withRSA`，签名串 = 除 sign 外全部参数按 key **字典序** `k=v&k=v`，结果 Base64 |
| 验签 | 响应与推送用票通平台公钥（X509）验签，失败抛 `SIGN_INVALID` |
| 信封流水号 | `serialNo` = 4 位平台简称 + 14 位日期时间 + 8 位 UUID（26 位） |
| 业务流水号 | `invoiceReqSerialNo` = 4 位平台简称 + 14 位日期时间 + 2 位随机（20 位，同一单据全程复用） |
| 传输层 | 与其他渠道一致，走 common `HttpTransport`，渠道包不直接依赖 OkHttp |

### 4.2 接口路径（全部 `.pt` 后缀）

| 编号 | 接口 | 路径 | 框架对接 |
|---|---|---|---|
| 2.4 | 获取登录短信验证码 | `/sendLoginSmsCode.pt` | `AuthSessionHandler.start` |
| 2.5 | 短信登录 | `/smsLogin.pt` | `AuthSessionHandler.submit(captcha)` |
| 2.6 | 获取实名认证二维码 | `/getAuthenticationQrcode.pt` | `AuthSessionHandler.start`（扫码） |
| 2.7 | 查询二维码扫码状态 | `/queryAuthQrcodeScanStatus.pt` | `AuthSessionHandler.submit(token)` |
| 2.8 | 查询数电账号认证状态 | `/getTaxBureauAccountAuthStatus.pt` | `start` 前置分流（`operationProposed=0` 免认证） |
| 2.9 | 开具蓝字数电发票 | `/invoiceBlue.pt` | `InvoiceIssuer.issue` |
| 2.23~2.27 | 特定业务开票 | **同一端点**，附加 `buildService` / `realEstateRentalList` 等节点 | 按核心模型国标字段自动附加 |
| 2.11 | 查询发票主要信息 | `/queryInvoiceInfo.pt` | `InvoiceQuerier.query` |
| 2.12 | 查询全票面 | `/queryInvoice.pt` | Provider 便捷入口 `queryFull(ctx)` |
| 2.13/2.14 | 推送主要/全票面（回调） | 第三方提供 | `InvoiceCallbackHandler` |
| 2.15 | 获取数电发票文件 | `/getAllEleInvFile.pt` | `InvoiceFileFetcher.fetch`（XML/PDF/OFD Base64 内容） |
| 2.19 | 重新发送邮件或短信 | `/resendEmailOrSMS.pt` | `InvoiceDeliverer.resend` |
| 2.38 | 红字确认单申请（新版） | `/redInvConfirmationApply.pt` | `RedHandler.apply`（`attrs.redApplyMode=OLD` 切 2.28 旧版） |
| 2.29 | 查看红字确认单 | `/querySpecialInvoiceRedInfo.pt` | `RedHandler.queryRed` |
| 2.30 | 开具红字数电发票 | `/issueRedSpecialInvoice.pt` | `RedHandler.issueRed` |
| 2.31 | 红字确认单审核 | `/confirmRedInvConfirmation.pt` | `RedHandler.confirm`（confirmType 0/1） |
| 2.33/2.34 | 局端索单下载/结果 | `/downloadSpecialInvRedInfoTable.pt`、`/queryRedInfoTableDownloadResult.pt` | Provider 便捷入口 `queryRedFromBureau(ctx, start, end)` |
| 2.45 | 查询数电账号列表 | `/listTaxBureauAccount.pt` | Provider 便捷入口 `listAccounts(ctx)` |

### 4.3 状态语义与归一

**业务状态码**（查询/推送/开票结果通用）：

| code | 语义 | 框架归一 |
|---|---|---|
| 0000 | 开票成功 | SUCCEEDED（XML 法定原件由编排层随后调 2.15 补拉归档） |
| 6666 | 未开票 | PROCESSING 保持挂起 |
| 7777 | 开票中 | PROCESSING |
| 9999 | 开票失败 | FAILED |
| 3999 | 需扫码/短信认证（响应带 authenticationQrcode/authId） | WAITING_AUTH |
| 4999 | 红字确认单申请中（仅红票） | PROCESSING |
| 5999 | 红字确认单审核中（仅红票） | PROCESSING |

**红字确认单 processState**：`81` 无需确认 / `84` 双方已确认 / `5` 已冲红 → CONFIRMED；
`82` 待购方确认 / `83` 待销方确认 / `0` 审核中 → WAITING_CONFIRM（72 小时窗）；
`4`/`7`/`11` 处理失败 → FAILED；`85`~`90`（否认/超时/撤销/异常凭证）→ REJECTED。

### 4.4 回调推送应答

推送请求为与响应相同的加密信封（平台公钥验签 + 3DES 解密）。
应答也走信封（业务部分 `code=0000/9999`）；因 SPI 的
`reply(success, message)` 无配置入参，应答信封依赖 `parse` 同线程暂存的配置
（ThreadLocal 桥接，解析异常即清理），拿不到时退化为业务明文 JSON——
宿主如需强制完整信封应答，可在回调 Controller 内直接用 Provider + ChannelConfig 构建。

### 4.5 配置项（`conflux.invoice.apps.<appId>.channels.piaotong`）

| 键 | 说明 |
|---|---|
| `attrs.platformCode` | 票通分配的第三方平台编码（8 位） |
| `attrs.aliasName` | 平台简称（4 位，拼流水号） |
| `attrs.password` | 3DES 密钥（加密/解密共用） |
| `attrs.privateKey` | 我方 RSA 私钥（PKCS8，请求签名） |
| `attrs.publicKey` | 票通平台公钥（X509，验签） |
| `attrs.sellerTaxNo` | 销方税号（报文 `taxpayerNum`） |
| `attrs.account` | 电子税局登录账号（认证链路） |
| `attrs.redApplyMode` | `OLD` 用 2.28 旧版红字申请（默认 2.38 新版） |
| `attrs.qrcodeType` | 2.6 二维码类型（1 税务 APP / 2 个税 APP，默认 1） |
| `attrs.blueInvoiceDate` / `attrs.applySource` / `attrs.redReason` | 红字申请覆盖项（购方申请时按需） |
| `attrs.goodsTransportList` 等 | 货运/旅客/不动产销售特定信息 JSON 原样透传 |

**未纳入**（与百旺口径一致）：作废类、快捷冲红（2.10/2.37）、索单撤销（2.32）、
企业注册（2.2）、二维码开票（2.16~2.18）、统计类、机动车（2.39~2.42）、
纸票（文档 3.2.0 未覆盖，生产在用的 `/invoicePaperCommon.pt` 等挂 C 档）。

## 5. 版式文件交付形态与固化策略

各平台的版式文件（XML/OFD/PDF/IMAGE）交付形态不一：有的在回调/查单响应里内联 Base64（如百旺票号回调、票通查单），
有的只给一个有时效的下载地址（如百旺 `qdbswj` 版式回调的 `xmlurl`/`ofdurl`/`pdf`）。下载链接普遍 1~7 天过期，
若只存 url 不取字节，过期后法定原件永久丢失。框架因此采用**归档即固化 + 按需加工**两层策略：

### 5.1 归档即固化（框架职责，使用方零感知）

- 编排层在把单据置为 `SUCCEEDED` 前，统一把 `content`（Base64/文本）与 `url` 两种形态拉平为**字节**：
  content 直接解码、url 立即下载（经 `HttpTransport.getBinary` 读原始字节，不走会破坏字节的 UTF-8 String 解码），
  回写 SHA-256 digest / size，可选落盘。
- **两级失败策略**：XML 法定原件固化失败一律阻断置成功（错误码 `XML_ORIGINAL_REQUIRED`）；
  PDF/OFD/IMAGE 展示文件失败仅告警、保留 url，由 `fileBytes`/`refresh`/`repair` 异步补拉，不阻断开票成功。
- 固化结果就地回填到 `InvoiceFile`（content/digest/size），`InvoiceFile.isMaterialized()` 标识已固化；SDK 无 FileStore，载体由使用方保管。
- 配置项 `conflux.invoice.file.*`：

| 键 | 默认值 | 说明 |
|---|---|---|
| `immediate-download` | `true` | 归档时对 url 型展示文件立即下载；XML 法定原件始终强制下载，不随开关变化 |
| `output-dir` | 空 | 本地落盘根目录；留空则字节仅在 `InvoiceFile` 载体中（SDK 无 FileStore，长期归档由使用方落对象存储） |
| `download-timeout-millis` | `30000` | 固化下载超时 |

### 5.2 统一字节出口与按需加工（门面 API）

| 门面方法 | 作用 |
|---|---|
| `materializeFiles(files, invoiceId, strict)` | 就地固化一批 `InvoiceFile`（url 型下载/Base64 解码后回填载体），strict 控制 XML 原件失败是否阻断 |
| `fileBytes(file)` / `fileBytes(files, type)` | 统一字节出口：content 直接解码；仅 url 则下载并就地回填；文件由使用方随结果取得并保管 |
| `pageImages(pdfBytes, config)` | 【按需】PDF 逐页转图（PNG，携带 DPI 元数据），供预览；转换时机与参数由使用方决定 |
| `printAdaptedPdf(pdfBytes, paper, dpi)` | 【按需】PDF 逐页栅格化后重组指定纸型 PDF（真实场景：特殊字体 PDF 直接打印丢字 → 转图再排进 A4，文字固化为像素），输出仍是单一 PDF，打印接口零改动 |

后两者基于 conflux-invoice-core 的 `PdfImageConverter`（运行依赖 PDFBox，开箱可用）。
框架绝不自动转换——是否转图、转什么纸型、什么时机，全部由使用方按需调用。
example 的 `InvoiceController` 提供了 `/api/invoices/files/bytes`、`/api/invoices/pdf/pages`、`/api/invoices/pdf/print-adapted` 演示端点。

## 6. 通用 REST 适配器的配置键

> 本节仅适用于复用 `AbstractRestInvoiceChannel` 的渠道；百旺金穗云的配置键见 3.4 节；票通云见 4.5 节。

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

## 7. 接入新平台的步骤

1. 在 `RestSpecs` 增加一份 `RestChannelSpec`，如实填写 `documentVerified` 与 `docNote`；
2. 常规平台新增一个继承 `AbstractRestInvoiceChannel` 的 Provider 类，在 starter 中注册为 Bean；
   协议差异过大时（如百旺金穗云的 Base64 报文 + 网关签名 + 双回调）应像
   `BaiwangJsChannelProvider` 一样直接实现 `InvoiceChannelProvider` 及各能力接口，不强行套通用适配器；
3. 若平台签名规则不是默认 HMAC-SHA256，覆写 `buildHeaders`；若响应结构与通用解析差异大，
   覆写 `call` 的解析或 `toInvoiceResult`；
4. 新增可选能力时在 SPI 上用 `default null`，并在注册表加 `capability()` 包装与能力位；
   未实现的渠道由注册表统一拒绝，不构成强制契约；
5. 在 `attrs` 中配置路径与票种编码映射，用 mock 单测覆盖「成功 / 异步 / 授权 / 失败 / 无 XML」分支；
6. 按平台文档核对字段语义后再将 `documentVerified` 置为 `true`。

## 8. 与支付域复用的部分

- `ChannelProvider` SPI + `ChannelRegistry` 按能力查找、缺失报明确异常；
- `AccountKey` 复合键与配置装载期 `putIfAbsent` + `CONFIG_CONFLICT` fail-fast；
- `HttpTransport`（common）统一 OkHttp 传输层，渠道不直接依赖 OkHttp；
- `ErrorCode` 三段分层（1xxx / 2xxx / 3xxx）；
- 无状态聚合：框架零台账、零状态机，判重/落库/状态推进全部交由使用方。

发票域额外引入的挂起态语义、授权会话载体、通道分级、XML 法定原件判定、两段红冲，
均为支付域没有的语义，详见 [README](../README.md)。
