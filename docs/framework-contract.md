# 五域框架统一契约（图纸 / 规范 / 检查清单）

支付、发票、通知、文件、登录五个能力域共用**同一套框架范式**。本文是这套范式的唯一权威定义：
既是当前结构的图纸，也是**以后新增能力或渠道必须严格执行的规范**。任何新增内容
若不符合本契约，视为未对齐，不予合并。

> 边界：本契约约束的是**框架用法**（门面、构建、注册、配置装载、starter 装配）。
> 各域业务方法名、业务参数、能力 handler 数量属于业务差异，不在统一范围内——
> 「统一」不等于「五域业务字段都相同」。

> 文件域定位：与通知域同属「薄门面委托第三方 SDK」型——内部全量委托
> [x-file-storage](https://x-file-storage.xuyanwu.cn/) 原始包（`x-file-storage-core`，
> 非其 starter），我们只做配置装载与出口门面，不写任何存储平台实现、不重做对方已有能力。

---

## 一、契约图纸：五域必须一致的部分

### 1.1 门面（Gateway）

| 契约 | 支付 | 发票 | 通知 | 文件 | 登录 | 约束 |
|---|---|---|---|---|---|---|
| 门面类名 | `PaymentGateway` | `InvoiceGateway` | `NotificationGateway` | `FileGateway` | `AuthGateway` | 一律 `XxxGateway` |
| 创建 | `builder()` | `builder()` | `builder()` | `builder()` | `builder()` | 一律静态 `builder()` |
| 配置源注入 | `.configProvider(cp)` | 同 | 同 | 同 | 同 | 同名，且**必填** |
| 构建校验 | 缺 `configProvider` 抛 `CONFIG_MISSING` | 同 | 同 | 同 | 同 | `build()` 必须校验非空 |
| 内置渠道 | `builder().build()` 自动全量注册 | 同 | SPI 全量注册 + 配置驱动装载 | 配置驱动自动构建 | `builder().build()` 经 SPI 全量注册 | 用户零 `.channel()` 即可用内置渠道 |
| 声明自定义渠道 | `.channel(provider)` | 同 | 同 | 同 | 同 | **仅自定义渠道扩展口**，同编码覆盖内置 |
| 注册渠道 | `registerChannel(provider)` | 同 | 同 | 同 | 同 | 同名，入参为各域 Provider，运行期增补 |
| 查看渠道 | `registeredChannels()` | 同 | 同 | 同 | 同 | 同名，返回渠道编码集合 |
| 线程安全 | 构建后不可变，方法可并发 | 同 | 同 | 同 | 同 | 门面无状态 |

### 1.2 配置源 SPI（ConfigProvider）

| 契约 | 支付 | 发票 | 通知 | 文件 | 登录 | 约束 |
|---|---|---|---|---|---|---|
| SPI 命名 | `ChannelConfigProvider` | `InvoiceConfigProvider` | `NotificationConfigProvider` | `FileConfigProvider` | `AuthConfigProvider` | 一律 `XxxConfigProvider` |
| 正向取配置 | `getConfig(configId, channelCode, …)` | 同（多 `sellerTaxNo`） | 同 | 同 | 同 | 一律按 configId 定位，方法名一律 `getConfig`；维度差异见白名单 |
| 内存实现 | `InMemoryChannelConfigProvider` | `InMemoryInvoiceConfigProvider` | `InMemoryNotificationConfigProvider` | `InMemoryFileConfigProvider` | `InMemoryAuthConfigProvider` | 一律 `InMemoryXxxConfigProvider` |
| Properties 实现 | `PropertiesChannelConfigProvider` | `PropertiesInvoiceConfigProvider` | `PropertiesNotificationConfigProvider` | `PropertiesFileConfigProvider` | `PropertiesAuthConfigProvider` | starter 默认实现，同名同构 |
| 读取时机 | 每次调用读取 | 同 | 同 | 同 | 同 | 凭证变更可被感知、客户端可重建 |
| 查无配置 | 抛 `CONFIG_MISSING` | 同 | 同 | 同 | 同 | 错误语义一致 |

### 1.3 渠道声明（Provider）

| 契约 | 支付 | 发票 | 通知 | 文件 | 登录 | 约束 |
|---|---|---|---|---|---|---|
| SPI 命名 | `ChannelProvider` | `InvoiceChannelProvider` | `NotificationChannel` | `FileChannelProvider` | `AuthChannel` | 一律 `XxxChannel(Provider)` |
| 渠道编码 | `channelCode()` | 同 | 同 | 同 | 同 | 一律 `channelCode()` |
| 注册表 | `ChannelRegistry` | `InvoiceChannelRegistry` | `NotificationGateway` 内部 Map（不暴露） | x-file-storage `FileStorageService`（内部 new） | `AuthGateway` 内部 Map（不暴露） | 均提供 `register(provider)` + `registeredChannels()` |
| 扩展自接渠道 | 实现 Provider + handler | 同 | 实现 `NotificationChannel`（继承 `AbstractNotificationChannel`）；短信渠道按 sms4j 三接口做 Factory 经 `SmsChannelProvider` 声明 | 按 x-file-storage 官方 `FileStorage`(+`FileStorageClientFactory`) | 实现 `AuthChannel`（继承 `AbstractAuthChannel`） | 内置与自定义走同一注册路径 |
| 渠道实现归属 | 自研 channels 模块 | 自研 channels 模块 | 统一渠道 SPI（短信现委托 sms4j，IM 平台自研 channels） | x-file-storage 原始包 | 自研 channels 模块（委托 wxjava/官方 SDK/标准 OAuth2） | 委托第三方 SDK 的渠道不重复实现其逻辑 |

> 文件域 Provider 是 final 声明类：承载一个 x-file-storage `FileStorage` 实例（或构建所需信息），
> `channelCode()` 取该实例的 `getPlatform()`；内置平台由配置驱动构建，自定义平台由使用方按
> 官方文档实现 `FileStorage` 后经 `FileChannelProvider.of(fileStorage)` 声明。注册时统一经
> `FileStorageServiceBuilder.addFileStorage(...)` 注入，内置与自定义同路。

### 1.4 配置统一规范（配置模型层 · 已落地）

> **状态**：本章为**已落地契约**。配置的"统一"指**配置模型同构**——五域都用
> 「`configId` + `channelCode` 公共字段 + `attr` 个性键值」的自有配置对象，
> 而不是五域字段完全相同，也不是方法名相同。五个域遵守以下六条硬契约（C1–C6）。

#### C1 配置对象模型：自有配置 + 公共字段一等化

任一域的配置都是**框架自有对象**（不暴露第三方官方配置类），由公共字段与 `attr` 组成：

```
XxxChannelConfig / XxxConfig        ← 框架自有，公共字段为一等字段 + attr
   （支付 ChannelConfig ← 各渠道 XxxParams extends；发票同理）
```

| 域 | 配置对象 | 公共字段（一等） | 渠道/平台字段 |
|---|---|---|---|
| 支付 | `ChannelConfig` ← 各渠道 `XxxParams extends` | configId / channelCode / notifyUrl / sandbox | 渠道凭证（merchantId、渠道 appId、apiV3Key、下沉的 subMchNo…） |
| 发票 | `ChannelConfig` ← 各渠道 Params extends | configId / channelCode / sellerTaxNo / notifyUrl / sandbox / tier / priority / enabled | 渠道凭证（appKey、drawer、销方名称/电话/地址/银行账号…） |
| 通知（短信） | `NotificationConfig`（自有，不含官方类） | configId / channelCode / accessKeyId / accessKeySecret / signature / templateId（= sms4j BaseConfig 公共凭证） | 厂商特有字段（regionId/sdkAppId/sender…） |
| 文件 | `FileChannelConfig`（自有） | configId / channelCode（内部映射官方 platform） | 官方平台配置字段（承载于官方 `FileStorageProperties`，整体交官方 builder） |
| 登录 | `AuthConfig`（自有，不含官方类） | configId / channelCode / clientId / clientSecret / redirectUri / scope（OAuth 公共字段） | 平台特有字段（私钥/证书/corpId/端点版本等，参数名不写死） |

- 支付/发票：各渠道 `XxxParams extends ChannelConfig`，公共字段在基类、渠道字段在子类。
- 短信：自有 `NotificationConfig`，公共凭证四件套一等化，`mergedAttr()` 合并后由装载器填充官方 Config，**不含 sms4j 官方类**。
- 文件：`FileChannelConfig` 暴露自有 `channelCode`，内部映射官方 platform；平台配置整体交给官方 `FileStorageServiceBuilder`，不逐字段解析。
- **禁止**：外壳对象 + 强类型 `params` 两层模型已拆除；渠道真实 appId 等 SDK 字段保留原样，不受 configId 改名影响。

#### C2 公共字段 vs `attr` 的唯一判定规则

| | 公共字段（一等） | `attr`（渠道/平台字段） |
|---|---|---|
| 判定 | 框架/门面需要读取做路由、通知覆盖、生命周期控制，或跨渠道同名同义且发调用必需 | 只有渠道 SDK/handler 认识、框架不解释其语义 |
| 键名 | 框架定义的固定字段名 | 对齐渠道/SDK 官方字段名 |
| 例 | configId / channelCode / notifyUrl / sandbox / sellerTaxNo / 短信凭证四件套 | 支付 apiV3Key/privateKey；发票 appKey/drawer；短信厂商特有；文件平台配置 |

- 短信公共字段取 sms4j `BaseConfig` 中跨渠道同名同义的凭证四件套（accessKeyId/accessKeySecret/signature/templateId），一等化声明。
- 同一语义字段只存一处：公共字段一等化，禁止在 `attr`、外壳、`params` 里复制副本。
- 公共字段权威：公共字段与 `attr` 同名时，**公共字段覆盖 `attr`**（如 `NotificationConfig.mergedAttr()`）。

#### C3 映射契约：统一映射器 + 渠道自有构建

- 公共基建：`ConfigMapper`（common 层，基于已有 Jackson `ObjectMapper`）提供 `fill/fromMap/normalize`；**不引入 hutool**。
- 支付/发票：各渠道 `XxxParams.fromMap(attr)` 负责把 attr 映射为强类型配置并做必填校验（渠道自有逻辑，含 equals 供客户端缓存复用）；starter 的 `Properties*` 把公共字段与 attr 一并装入。
- 短信：自有 `NotificationConfig` 一等字段 + `mergedAttr()`，装载器用官方 Factory 构建厂商 Config。
- 文件：`FileProperties` 由 Spring Boot 原生绑定到官方 `FileStorageProperties`，整体交官方 builder。
- 委托 SDK 域内部用官方原始包构建配置对象（sms4j 官方 Factory / x-file-storage 官方 builder），不重做对方能力。

#### C4 两种运行形态共用同一配置模型

| 形态 | 配置来源 | 构建配置对象 |
|---|---|---|
| Boot starter | yml `conflux.<域>.configs.<configId>.<channelCode>` | `Properties*` 读 yml → 公共字段一等 + `attr` → 自有 config |
| 纯 Java / MVC | 代码构造公共字段 + `attr` | `InMemory*.register(configId, channelCode, …)` → 自有 config |

- 两形态**共用同一自有配置对象**；差异只在"字段/attr 从哪来"，不在配置模型。
- `XxxConfigProvider` SPI（1.2）保留：运行期每次取配置，查无抛 `CONFIG_MISSING`，与配置模型解耦。

#### C5 配置路径与命名

- 路径一律 `conflux.<域>.configs.<configId>.<channelCode>`，个性键值一律装进 **`attr`（单数）**。
- 框架配置定位键一律 `configId`（复数路径 `configs`）；**渠道真实 appId（微信/支付宝/attr.appId）原样保留**，不受影响。
- 渠道字段对外名一律 `channelCode`，内部映射到短信 `factory` / 文件 `platform`。
- 文件域 `<channelCode>` 即平台实例标识（如 `aliyun-oss-1`，实例名不是类型名），内部映射官方 platform。
- 禁止 `attrs`（复数）与框架层 `appId`/`apps`；getter/setter 一律 `getAttr`/`setAttr`。

**渠道编码（channelCode）命名口径（五域通用，登录域已是事实标准）**：

- 平台前缀用全称 `wechat` / `alipay` / `feishu` / `dingtalk`（见 common 的 `Platforms`），禁止 `wx` / `weixin` / `ali` / `ding` 缩写。
- 企业微信**不单列平台**，归属微信生态、统一用 `wechat_wecom_*` 前缀（如 `wechat_wecom_web`），不写 `wecom` / `wxwork` / `qywx`。
- 后缀按**应用形态 / 凭证归属**取 `mini`（小程序）/ `mp`（公众号）/ `open`（开放平台）/ `app` / `web` / `h5` / `qr` 等；同形态不同端再细分（如 `alipay_h5_jsapi`）。
- **编码粒度 = 一份凭证 / 一个应用形态，不是消息类型或业务动作**：同一份凭证能干的事共用一个 code，靠不同参数对象区分具体动作。例：微信小程序订阅/客服/统一消息共用 `wechat_mini`，由入参 bean（如 `MiniSubscribeMsg`）区分消息类型；不可拆成 `wechat_mini_subscribe`、`wechat_mini_custom`。
- 因此各域**复用同一套编码**：`wechat_mp` 这份公众号凭证在登录域做网页授权、在通知域发模板消息，编码相同，动作由业务方法（`authorize` / `send`）与参数对象区分。登录域既有 24 形态编码即此口径的事实标准，新增渠道不得另起命名体系。

#### C6 委托 SDK 域的对齐方式

- **自有配置，不含官方类**：短信用 `NotificationConfig`、文件用 `FileChannelConfig`，用户不接触官方 Config 类。
- **装载对齐**：短信把一等凭证 + attr 合并后由官方 Factory 构建厂商 Config；文件把官方 `FileStorageProperties` 整体交官方 builder。
- **门面/SPI 对齐**：与五域共用 1.1/1.2 的门面与配置源契约。

#### 五域落地对照（已落地）

| 域 | 配置对象 | 映射/装载 | 公共字段（示例） |
|---|---|---|---|
| 支付 | `ChannelConfig` ← `XxxParams extends` | `XxxParams.fromMap` + starter 公共字段 | configId/channelCode/notifyUrl/sandbox |
| 发票 | `ChannelConfig` ← 渠道 Params extends | 渠道 fromMap + starter 公共字段 | configId/channelCode/sellerTaxNo/notifyUrl/sandbox/tier/priority |
| 通知（短信） | `NotificationConfig`（自有） | `Sms4jChannel` → `mergedAttr()` → sms4j 官方 Factory | configId/channelCode/accessKeyId/accessKeySecret/signature/templateId |
| 文件 | `FileChannelConfig`（自有） | 官方 `FileStorageServiceBuilder` | configId/channelCode（→ 官方 platform） |
| 登录 | `AuthConfig`（自有） | starter `PropertiesAuthConfigProvider` / 纯 Java 直接构造 | configId/channelCode/clientId/clientSecret/redirectUri/scope |

### 1.5 模块与运行形态

| 契约 | 约束 |
|---|---|
| 模块划分 | `Xxx-core`（纯 Java，零 Spring）+ `Xxx-channels`（自研渠道实现；委托第三方 SDK 的渠道无此模块）+ `Xxx-spring-boot-starter` |
| 核心依赖 | core 零 Spring；Spring 只出现在 starter 与测试范围 |
| 运行形态 | 纯 Java jar 手动 `builder()`；Boot 由 starter 自动装配为单例 Bean |
| 版本管理 | 第三方 SDK 版本在 BOM 单源管理 |
| JDK / Boot | 当前工程 JDK8 + Boot2.7.18；SDK 需 JDK8 兼容 |

> 文件域无 channels 模块（渠道实现全在 x-file-storage 原始包）；通知域短信渠道委托 sms4j 原始包、
> 由 BOM 单源管理其版本；各存储平台 SDK（阿里云 OSS、MinIO 等）官方按使用方需要按需引入，
> 我们不在自身 POM 里聚合所有平台 SDK。

### 1.6 starter 装配（自动装配层）

| 契约 | 支付 | 发票 | 通知 | 文件 | 登录 | 约束 |
|---|---|---|---|---|---|---|
| 装配类命名 | `PayGatewayAutoConfiguration` | `InvoiceAutoConfiguration` | `NotificationAutoConfiguration` | `FileAutoConfiguration` | `AuthAutoConfiguration` | 一律 `XxxAutoConfiguration`（支付历史名保留） |
| 配置源 Bean | `@ConditionalOnMissingBean` 默认 Properties 实现 | 同 | 同 | 同 | 同 | 可被同类型 Bean 覆盖 |
| 门面 Bean | `@ConditionalOnMissingBean(PaymentGateway)` | 同 | 同 | `@ConditionalOnMissingBean(FileGateway)` | `@ConditionalOnMissingBean(AuthGateway)` | 可被同类型 Bean 覆盖 |
| 配置源注入 | `ObjectProvider` + 缺失抛异常 | 同 | 同 | 同 | 同 | 五域统一 fail-fast |
| 内置渠道注册 | `builder().build()` 经 SPI 清单自动全量注册 | 同 | SDK/classpath 自动发现 | 配置驱动自动构建 | `builder().build()` 经 SPI 清单自动全量注册 | starter 不逐个声明内置渠道 Bean |
| 自定义渠道收集 | `List<ChannelProvider>` 注入，逐个 `builder.channel(...)` | 同 | 同 | `List<FileChannelProvider>` 同构收集 | `List<AuthChannel>` 同构收集 | 仅收集框架未内置的自定义渠道 |
| mock 开关 | `conflux.pay.mock-enabled` | `conflux.invoice.mock-enabled` | 无 mock 概念 | 复用文件平台自身（如 local），无额外 mock 开关 | 无 mock 概念 | 有 mock 的域统一用 `<域>.mock-enabled`（默认开） |
| Clock | `ObjectProvider<Clock>` 可选注入 | 同 | 无时间窗口能力 | 无时间窗口能力 | 无时间窗口能力 | 提供 `Builder.clock()` 的域统一接容器 Clock |
| 专属 SPI 注入 | — | — | — | — | `ObjectProvider<StateCache>` 可选注入 | 域专属能力（登录 state 缓存，默认内存） |
| 门面装配 | 注入 configProvider → 自定义渠道 → `build()`（内部自动注册内置渠道）→ 打印 `registeredChannels()` | 同 | 同 | 同 | 同（另注入可选 StateCache） | 装配流水线一致，无内置渠道循环 |
| Registry Bean | 不暴露（网关内部 new） | 不暴露（网关内部 new） | sms4j 全局 `ProviderFactoryHolder` | 不暴露（网关内部 new `FileStorageService`） | 不暴露（网关内部 Map） | starter 不额外暴露 Registry Bean |

> 内置渠道注册机制：core 定义 `BuiltinChannelRegistrar` SPI，channels 模块以 Java SPI 清单
> 提供内置渠道实现（带可选 SDK 缺失容错），`builder().build()` 加载并注册；同编码时自定义渠道
> （`.channel()` 先注册）覆盖内置。starter 不再逐个声明内置渠道 Bean，也不循环注册内置渠道。

> 说明：通知域已引入统一渠道表（同登录域），短信渠道经 `Sms4jChannel` 委托 sms4j、
> 与已落地的微信 IM 渠道在门面层完全同构。starter 层已完全对齐（上表五域框架项逐项一致）。通知（短信）/文件的"无自有 Registry / 无 Clock
> / 无 mock（文件）"源于渠道实现委托第三方原始包，登录的"无 Clock / 可选 StateCache"源于其业务特性，
> 均属能力差异（见第二章白名单），不是框架装配不统一。

### 1.7 文件域门面业务方法与 FileRecorder 边界（已确认口径）

文件域业务方法名按域语义命名（属白名单），但方法清单已锁定，不得随意扩张——
凡是 x-file-storage 已提供的能力，用户走 `nativeService()` 出口直接用原生，门面不重做。

| 类别 | 方法 | 入参形态 | 说明 |
|---|---|---|---|
| 基线 | `upload(configId, channelCode, file, …)` | 文件源 | 委托 `FileStorageService.of(...).setPlatform(...).upload()`，返回原始 `FileInfo` |
| 基线 | `download(configId, channelCode, fileInfo)` | `FileInfo` | 委托 `service.download(fileInfo)`，返回原始 `Downloader` |
| 基线 | `delete(configId, channelCode, fileInfo)` | `FileInfo` | 委托 `service.delete(fileInfo)` |
| 基线 | `exists(configId, channelCode, fileInfo)` | `FileInfo` | 委托 `service.exists(fileInfo)` |
| 基线 | `nativeService(configId, channelCode)` | — | 返回原生 `FileStorageService`，进阶能力直接用原生 |
| 放开 B | `generatePresignedUrl(configId, channelCode, fileInfo, expiration)` | `FileInfo` | 委托 `service.generatePresignedUrl(fileInfo, expiration)`，返回 URL 字符串 |
| 放开 C | `copy(...)` / `move(...)` | `FileInfo` | 委托 `service.copy(fileInfo)` / `move(fileInfo)`，返回原生 Pretreatment 供链式 |

**FileRecorder 边界（Q3 方案一，已确认）**：

- 我们**不实现、不托管** `FileRecorder`，不碰任何 DB；构建走官方
  `FileStorageServiceBuilder.create(properties).useDefault().build()`。
- `useDefault()` 内置官方 `DefaultFileRecorder`：空实现（save/delete 返回 true、
  `getByUrl` 抛明确异常"尚未实现 FileRecorder"），因此 `upload/delete(FileInfo)` 正常可用，
  无需我们写任何 no-op。
- 因此门面**一律以 `FileInfo` 为入参/返回**，**不暴露** `getFileInfoByUrl`、`download(url)`、
  `delete(url)`、`exists(url)` 等一切依赖 url→记录反查的方法（这些方法在默认 recorder 下会抛异常）。
- 使用方若实现了自己的 `FileRecorder`（DB），其按 url 查询等能力请走 `nativeService()` 原生出口，
  不进门面契约。

---

## 二、差异白名单（允许不同，不算不统一）

以下差异源于业务本质，**不得为了"形式统一"而强行拉平**（拉平会造空参数、丢能力）：

| 差异 | 支付 | 发票 | 通知 | 文件 | 登录 | 为什么允许 |
|---|---|---|---|---|---|---|
| `getConfig` 维度 | 2 参 `configId+channelCode` | 3 参多 `sellerTaxNo` | 2 参 | 2 参 | 2 参 | 发票一家企业多销方，销方税号是必需定位维度 |
| 配置 SPI 其他方法 | — | `instances` | `allConfigs` | `allConfigs` | `allConfigs`（Map） | 通道路由 / 启动预热是各域能力差异；accountKey 反查整套已删除 |
| Provider 形态 | interface + 多 handler | interface + 多 handler | interface `NotificationChannel` + 模板基类 `AbstractNotificationChannel`（短信实现委托 sms4j） | final 类承载 x-file-storage `FileStorage` | interface `AuthChannel` + 模板基类 `AbstractAuthChannel` | 短信/文件渠道实现全在第三方，Provider 只做声明；登录 24 形态自研 |
| Builder 专属项 | `clock()` | `clock()` + `fileOptions()` | 无 | 无 | `stateCache()` | 版式文件、时间窗口、登录 state 缓存是域专属能力 |
| starter 渠道装配 | SPI 内置全量注册 + 自定义渠道 Bean | 同 | 无渠道 Bean | `List<FileChannelProvider>` 收集自定义平台 | SPI 内置全量注册 + `List<AuthChannel>` 自定义 | 短信/文件渠道实现全在第三方，配置驱动、无自有 Registry/Clock/mock |
| 业务方法名 | `createPayment` 等 | `issue` / `applyRed` 等 | `send`（短信/推送共用） | `upload` / `download` 等 | `authorize` / `parseCallback` | 按域语义命名；登录回调是"换身份"而非"验签归一" |
| 错误码表达 | 框架抛 2xxx + 结果字符串码 | 3xxx | 4xxx | 5xxx | 框架抛 6xxx + 结果字符串码 | 五域结果对象的 `errorCode` 均为字符串，渠道侧语义由各域表达 |
| 记录能力 | 无状态 | 无状态 | 无 | 委托 x-file-storage `DefaultFileRecorder`（空实现），不托管 DB | 无状态（state 短时缓存走 SPI，不入业务库） | 文件域 FileInfo 由调用方持有，url 反查依赖使用方自实现 recorder，属能力差异 |

> 回调配置定位：验签仍是框架职责，但配置定位权在使用方——业务按自己的订单号查库得到
> `configId`（发票再加 `sellerTaxNo`）后显式传入 `parseCallback(configId, request)`，
> 框架据此 `getConfig` 验签，不再从回调报文隐式反查账户键（accountKey 整套已删除）。

---

## 三、新增能力 / 渠道的检查清单（严格执行）

新增任何能力或渠道前，逐项打勾；任一项不满足即视为未对齐。

### 3.1 新增一个能力域（复制范式）

- [ ] 模块结构：`Xxx-core` 零 Spring + `Xxx-channels`（如自研渠道；委托第三方 SDK 的域无此模块）+ `Xxx-spring-boot-starter`
- [ ] 门面 `XxxGateway`：`builder()` / `configProvider()`（必填 + build 校验）/ `channel(provider)` / `registerChannel(provider)` / `registeredChannels()`
- [ ] 配置 SPI `XxxConfigProvider`：`getConfig` + `InMemoryXxxConfigProvider` + starter 的 `PropertiesXxxConfigProvider`
- [ ] 渠道 SPI `XxxChannelProvider`：`channelCode()` + 注册表 `register`/`registeredChannels`
- [ ] 配置路径 `conflux.xxx.configs.<configId>.<channelCode>` + `attr`（单数，C5）
- [ ] 配置模型：自有 config + 公共字段一等化 + `attr`，无外壳+params 两层结构（C1）
- [ ] 公共字段一等化、渠道字段进 attr，单一判定规则、无重复副本（C2）
- [ ] 公共/attr 映射走 common 的 `ConfigMapper`（Jackson），不引 hutool、不重复手写（C3）
- [ ] Boot 与纯 Java 两形态共用同一自有配置对象与映射模型（C4）
- [ ] 配置源每次读取、凭证变更可重建；查无配置抛 `CONFIG_MISSING`
- [ ] starter：`XxxAutoConfiguration` 仅收集自定义 `List<XxxChannelProvider>`、内置渠道由 builder SPI 自动注册、默认配置源可覆盖、缺失 fail-fast
- [ ] 错误码独占号段（支付 2xxx / 发票 3xxx / 通知 4xxx / 文件 5xxx / 登录 6xxx）
- [ ] 纯 Java + Boot 双形态均验证；BOM 单源管理版本
- [ ] 委托第三方 SDK 的渠道（通知域短信渠道 / 文件域）：内部全用原始包，不引对方 starter，不重做对方已有能力

### 3.2 新增一个支付 / 发票渠道

- [ ] 定义渠道 `XxxParams extends XxxChannelConfig`：公共字段在基类、渠道字段在子类，`fromMap(attr)` 映射并校验（C1/C3）
- [ ] 实现 `ChannelProvider` / `InvoiceChannelProvider`：`channelCode()` + `parseParams(attr)` + 所需 handler（不支持的返回 null），并在 channels 的 SPI 清单登记
- [ ] 渠道 SDK、签名、协议、字段映射全部封闭在渠道包内部，不泄漏到 core
- [ ] 渠道路由方式编码（支付 `PayMethod` / 发票票种与通道分级）在渠道内声明
- [ ] 渠道 SDK 缺失时由 SPI 清单的 try-catch 容错跳过，无需 starter 逐个声明渠道 Bean
- [ ] yml 配置示例 + `docs/config-keys.md`（支付）/ 对应发票文档 补充键说明
- [ ] 渠道单测：签名向量、回调验签、关键能力 happy path（不触发真实网络）
- [ ] 接入真实账号后按 `docs/integration-checklist.md` 联调

### 3.3 新增一个通知渠道（短信 / IM 推送）

通知域统一渠道表 `Map<channelCode, NotificationChannel>`，一条 code 只对应一个渠道，无归属歧义。按渠道类型二选一：

**A. 短信渠道（委托 sms4j，不写自有实现）**

- [ ] 优先确认 sms4j 是否已内置；内置则仅加配置即可，零框架代码
- [ ] sms4j 清单外渠道：按 [sms4j 官方自定义扩展](https://sms4j.com/doc3/custom.html) 实现 `SmsBlend` / `SupplierConfig` / `BaseProviderFactory`
- [ ] 用 `SmsChannelProvider.of(factory)` 声明，由 `Sms4jChannel` 统一委托，**不写我们自己的渠道实现**
- [ ] 自有 `NotificationConfig` 一等凭证字段（accessKeyId/accessKeySecret/signature/templateId），厂商特有字段放 `attr`（C2）
- [ ] 公共字段与 `attr` 经 `mergedAttr()` 合并，由 sms4j 官方 Factory 构建厂商 Config（内部用官方包）
- [ ] 内置与自定义走同一 `ProviderFactoryHolder` 原生注册路径

**B. IM 推送渠道（自研，放 `conflux-notify-channels`）**

- [ ] 实现 `NotificationChannel`（建议继承 `AbstractNotificationChannel` 复用上下文回填与异常归一）
- [ ] `channelCode()` 标识平台/应用形态（如 `wechat_mini`），一份凭证一个 code；消息类型由入参 bean 区分、不进 code（C5）；全局唯一、不与短信供应商码冲突；SDK/协议/参数名封闭在渠道包内
- [ ] 凭证与平台参数从 `NotificationConfig` 的一等公共字段 + `attr` 读取（C1/C2），不新增配置 SPI
- [ ] 客户端/token 由渠道内部按凭证指纹缓存、凭证变更重建；不引 Redis，不建跨域公共层
- [ ] 在 channels 模块的 SPI 清单登记为内置渠道，构建期自动注册；单测用 mock SDK，不触发真实网络

### 3.4 新增一个文件平台 / 文件能力

- [ ] 只用 `x-file-storage-core` 原始包，不引 `x-file-storage-spring` starter；各存储平台 SDK 由使用方按需引入
- [ ] 内置平台由配置驱动（对外 `channelCode`，内部 platform 实例标识），装载期 `FileStorageServiceBuilder.create(props).useDefault().build()` 构建，不写任何平台实现
- [ ] 自定义平台：使用方按 [x-file-storage 官方扩展](https://x-file-storage.xuyanwu.cn/)实现 `FileStorage`（可选 `FileStorageClientFactory`），用 `FileChannelProvider.of(fileStorage)` 声明
- [ ] 内置与自定义平台统一经 `addFileStorage(...)` 注入，走同一注册路径
- [ ] 门面方法严格限于章节 1.7 清单（基线 4 + nativeService + B/C），不重做 x-file-storage 已有能力；进阶能力一律走 `nativeService()` 原生出口
- [ ] 不实现/不托管 `FileRecorder`：门面一律 `FileInfo` 入参，禁止暴露 `getFileInfoByUrl` 及一切 url 反查方法（Q3 方案一）
- [ ] yml 配置示例 + 文档补充平台键说明；核心单测不触发真实网络

### 3.5 任何改动的统一性回归

- [ ] 五域门面框架方法签名保持本文章节 1.1 一致（内置渠道 build 自动注册、`.channel()` 仅自定义）
- [ ] 五域配置源 SPI 保持章节 1.2 约定（必填、每次读取、`getConfig` 同名）
- [ ] **五域配置模型保持章节 1.4 的 C1–C6 同构**（不只是方法名统一）
- [ ] 公共字段判定一致、`attr` 单数命名、无重复副本、唯一 `of(Map)` 映射入口
- [ ] 未把业务差异"拉平"成空参数或伪能力（对照章节二白名单）
- [ ] 全量 `mvn clean test` 通过

---

## 四、相关文档

- 通用入门：[getting-started.md](getting-started.md)
- 使用指南：[支付](guides/pay.md) · [发票](guides/invoice.md) · [通知](guides/notify.md) · [文件](guides/file.md) · [登录](guides/auth.md)
- API 参考：[reference/api-reference.md](reference/api-reference.md)
- 配置键：[reference/config-keys.md](reference/config-keys.md)（支付）、[reference/invoice-platforms.md](reference/invoice-platforms.md)（发票）
- 联调清单：[integration-checklist.md](integration-checklist.md)
