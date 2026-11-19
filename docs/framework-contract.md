# 三域框架统一契约（图纸 / 规范 / 检查清单）

支付、发票、短信三个能力域共用**同一套框架范式**。本文是这套范式的唯一权威定义：
既是当前结构的图纸，也是**以后新增能力或渠道必须严格执行的规范**。任何新增内容
若不符合本契约，视为未对齐，不予合并。

> 边界：本契约约束的是**框架用法**（门面、构建、注册、配置装载、starter 装配）。
> 各域业务方法名、业务参数、能力 handler 数量属于业务差异，不在统一范围内——
> 「统一」不等于「三域业务字段都相同」。

---

## 一、契约图纸：三域必须一致的部分

### 1.1 门面（Gateway）

| 契约 | 支付 | 发票 | 短信 | 约束 |
|---|---|---|---|---|
| 门面类名 | `PaymentGateway` | `InvoiceGateway` | `SmsGateway` | 一律 `XxxGateway` |
| 创建 | `builder()` | `builder()` | `builder()` | 一律静态 `builder()` |
| 配置源注入 | `.configProvider(cp)` | 同 | 同 | 同名，且**必填** |
| 构建校验 | 缺 `configProvider` 抛 `CONFIG_MISSING` | 同 | 同 | `build()` 必须校验非空 |
| 声明渠道 | `.channel(provider)` | 同 | 同 | Builder 与门面双入口 |
| 注册渠道 | `registerChannel(provider)` | 同 | 同 | 同名，入参为各域 Provider |
| 查看渠道 | `registeredChannels()` | 同 | 同 | 同名，返回渠道编码集合 |
| 线程安全 | 构建后不可变，方法可并发 | 同 | 同 | 门面无状态 |

### 1.2 配置源 SPI（ConfigProvider）

| 契约 | 支付 | 发票 | 短信 | 约束 |
|---|---|---|---|---|
| SPI 命名 | `ChannelConfigProvider` | `InvoiceConfigProvider` | `SmsConfigProvider` | 一律 `XxxConfigProvider` |
| 正向取配置 | `getConfig(appId, channel, …)` | 同 | 同 | 方法名一律 `getConfig`；维度差异见白名单 |
| 内存实现 | `InMemoryChannelConfigProvider` | `InMemoryInvoiceConfigProvider` | `InMemorySmsConfigProvider` | 一律 `InMemoryXxxConfigProvider` |
| Properties 实现 | `PropertiesChannelConfigProvider` | `PropertiesInvoiceConfigProvider` | `PropertiesSmsConfigProvider` | starter 默认实现，同名同构 |
| 读取时机 | 每次调用读取 | 同 | 同 | 凭证变更可被感知、客户端可重建 |
| 查无配置 | 抛 `CONFIG_MISSING` | 同 | 同 | 错误语义一致 |

### 1.3 渠道声明（Provider）

| 契约 | 支付 | 发票 | 短信 | 约束 |
|---|---|---|---|---|
| SPI 命名 | `ChannelProvider` | `InvoiceChannelProvider` | `SmsChannelProvider` | 一律 `XxxChannelProvider` |
| 渠道编码 | `channelCode()` | 同 | 同 | 一律 `channelCode()` |
| 注册表 | `ChannelRegistry` | `InvoiceChannelRegistry` | sms4j `ProviderFactoryHolder` | 均提供 `register(provider)` + `registeredChannels()` |
| 扩展自接渠道 | 实现 Provider + handler | 同 | 按 sms4j 官方三接口做 Factory | 内置与自定义走同一注册路径 |
| 渠道实现归属 | 自研 channels 模块 | 自研 channels 模块 | sms4j 原始包 | 短信不重复实现渠道逻辑 |

### 1.4 配置结构

| 域 | 配置路径 | 统一键 | 个性键 |
|---|---|---|---|
| 支付 | `conflux.pay.apps.<appId>.<channel>` | 公共字段 | `attrs` |
| 发票 | `conflux.invoice.apps.<appId>.<channel>` | 公共字段 | `attrs` |
| 短信 | `conflux.sms.apps.<appId>.<channel>` | sms4j `BaseConfig` 公共字段下沉 | `attrs`（键名对齐 sms4j Config 字段） |

一律 `conflux.<域>.apps.<appId>.<channel>` + `attrs` 两层结构；统一键优先、`attrs` 兜底。

### 1.5 模块与运行形态

| 契约 | 约束 |
|---|---|
| 模块划分 | `Xxx-core`（纯 Java，零 Spring）+ `Xxx-channels`（渠道实现，短信无此模块）+ `Xxx-spring-boot-starter` |
| 核心依赖 | core 零 Spring；Spring 只出现在 starter 与测试范围 |
| 运行形态 | 纯 Java jar 手动 `builder()`；Boot 由 starter 自动装配为单例 Bean |
| 版本管理 | 第三方 SDK 版本在 BOM 单源管理 |
| JDK / Boot | JDK17 + Boot3 无需改造 |

### 1.6 starter 装配（自动装配层）

| 契约 | 支付 | 发票 | 短信 | 约束 |
|---|---|---|---|---|
| 装配类命名 | `PayGatewayAutoConfiguration` | `InvoiceAutoConfiguration` | `SmsAutoConfiguration` | 一律 `XxxAutoConfiguration`（支付历史名保留） |
| 配置源 Bean | `@ConditionalOnMissingBean` 默认 Properties 实现 | 同 | 同 | 可被同类型 Bean 覆盖 |
| 门面 Bean | `@ConditionalOnMissingBean(PaymentGateway)` | 同 | 同 | 可被同类型 Bean 覆盖 |
| 配置源注入 | `ObjectProvider` + 缺失抛异常 | 同 | 同 | 三域统一 fail-fast |
| 渠道收集 | `List<ChannelProvider>` 注入，逐个 `builder.channel(...)` | 同 | 同 | 统一收集姿势 |
| mock 开关 | `conflux.pay.mock-enabled` | `conflux.invoice.mock-enabled` | 无 mock 概念 | 有 mock 的域统一用 `<域>.mock-enabled`（默认开） |
| Clock | `ObjectProvider<Clock>` 可选注入 | 同 | 无时间窗口能力 | 提供 `Builder.clock()` 的域统一接容器 Clock |
| 门面装配 | 收集渠道 → 注入 configProvider → `build()` → 打印 `registeredChannels()` | 同 | 同 | 装配流水线一致 |
| Registry Bean | 不暴露（网关内部 new） | 不暴露（网关内部 new） | sms4j 全局 `ProviderFactoryHolder` | starter 不额外暴露 Registry Bean |

> 说明：starter 层已完全对齐（上表三域逐项一致）。短信"无渠道 Bean / 无自有 Registry / 无 Clock / 无 mock"
> 源于渠道实现委托 sms4j 原始包，属能力差异（见第二章白名单），不是框架装配不统一。

---

## 二、差异白名单（允许不同，不算不统一）

以下差异源于业务本质，**不得为了"形式统一"而强行拉平**（拉平会造空参数、丢能力）：

| 差异 | 支付 | 发票 | 短信 | 为什么允许 |
|---|---|---|---|---|
| `getConfig` 维度 | 2 参 `appId+channel` | 3 参多 `sellerTaxNo` | 2 参 | 发票一家企业多销方，销方税号是必需定位维度 |
| 配置 SPI 其他方法 | `getByAccountKey` | `getByAccountKey` + `instances` | `allConfigs` | 回调反查 / 通道路由 / 启动预热是各域能力差异 |
| Provider 形态 | interface + 多 handler | interface + 多 handler | final 类承载 sms4j Factory | 短信渠道实现全在 sms4j，Provider 只做声明 |
| Builder 专属项 | `clock()` | `clock()` + `fileOptions()` | 无 | 版式文件、时间窗口是域专属能力 |
| starter 渠道装配 | 渠道 Bean 按类路径激活 | 同 | 无渠道 Bean | 短信渠道实现全在 sms4j，配置驱动、无自有渠道 Bean/Registry/Clock/mock |
| 业务方法名 | `createPayment` 等 | `issue` / `applyRed` 等 | `send` | 按域语义命名 |

---

## 三、新增能力 / 渠道的检查清单（严格执行）

新增任何能力或渠道前，逐项打勾；任一项不满足即视为未对齐。

### 3.1 新增一个能力域（复制范式）

- [ ] 模块结构：`Xxx-core` 零 Spring + `Xxx-channels`（如自研渠道）+ `Xxx-spring-boot-starter`
- [ ] 门面 `XxxGateway`：`builder()` / `configProvider()`（必填 + build 校验）/ `channel(provider)` / `registerChannel(provider)` / `registeredChannels()`
- [ ] 配置 SPI `XxxConfigProvider`：`getConfig` + `InMemoryXxxConfigProvider` + starter 的 `PropertiesXxxConfigProvider`
- [ ] 渠道 SPI `XxxChannelProvider`：`channelCode()` + 注册表 `register`/`registeredChannels`
- [ ] 配置路径 `conflux.xxx.apps.<appId>.<channel>` + `attrs`
- [ ] 配置源每次读取、凭证变更可重建；查无配置抛 `CONFIG_MISSING`
- [ ] starter：`XxxAutoConfiguration` 收集 `List<XxxChannelProvider>`、默认配置源可覆盖、缺失 fail-fast
- [ ] 错误码独占号段（支付 2xxx / 发票 3xxx / 短信 4xxx）
- [ ] 纯 Java + Boot 双形态均验证；BOM 单源管理版本

### 3.2 新增一个支付 / 发票渠道

- [ ] 实现 `ChannelProvider` / `InvoiceChannelProvider`：`channelCode()` + `parseParams()` + 所需 handler（不支持的返回 null）
- [ ] 渠道 SDK、签名、协议、字段映射全部封闭在渠道包内部，不泄漏到 core
- [ ] 渠道路由方式编码（支付 `PayMethod` / 发票票种与通道分级）在渠道内声明
- [ ] starter 增加 `@Bean @ConditionalOnMissingBean + @ConditionalOnClass` 装配（有第三方 SDK 依赖时）
- [ ] yml 配置示例 + `docs/config-keys.md`（支付）/ 对应发票文档 补充键说明
- [ ] 渠道单测：签名向量、回调验签、关键能力 happy path（不触发真实网络）
- [ ] 接入真实账号后按 `docs/integration-checklist.md` 联调

### 3.3 新增一个短信渠道

- [ ] 优先确认 sms4j 是否已内置；内置则仅加配置即可，零框架代码
- [ ] sms4j 清单外渠道：按 [sms4j 官方自定义扩展](https://sms4j.com/doc3/custom.html) 实现 `SmsBlend` / `SupplierConfig` / `BaseProviderFactory`
- [ ] 用 `SmsChannelProvider.of(factory)` 声明，**不写我们自己的渠道实现**
- [ ] 统一键（accessKeyId 等公共字段）放外层，`attrs` 放厂商特有键
- [ ] 内置与自定义走同一 `ProviderFactoryHolder` 原生注册路径

### 3.4 任何改动的统一性回归

- [ ] 三域门面方法签名保持本文章节 1.1 一致
- [ ] 三域配置源 SPI 保持章节 1.2 约定（必填、每次读取、`getConfig` 同名）
- [ ] 未把业务差异"拉平"成空参数或伪能力（对照章节二白名单）
- [ ] 全量 `mvn clean test` 通过

---

## 四、相关文档

- API 参考：[api-reference.md](api-reference.md)
- 配置键：[config-keys.md](config-keys.md)（支付）、[invoice-platforms.md](invoice-platforms.md) §3.4/§4.5/§6（发票）、README「能力域三：短信」
- 接入流程：[pay-integration.md](pay-integration.md)
- 联调清单：[integration-checklist.md](integration-checklist.md)
