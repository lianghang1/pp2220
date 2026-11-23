# 登录域使用指南

登录域提供国内第三方登录的统一门面：微信（7 形态）、支付宝（6 形态）、钉钉（6 形态）、飞书（5 形态），共 **24 种形态**。门面**只做路由**：按 `configId` + `channelCode` 取配置、找到渠道、转发；协议细节、票据交换、身份归一全部封闭在各渠道内部。通用契约见 [getting-started.md](../getting-started.md)。

> 形态清单与底层库选型见项目根目录 `conflux-auth-登录形态清单.md`；接口设计见 `conflux-auth-门面接口设计.md`。

## 1. 引入

### Spring Boot starter（推荐）

```xml
<dependency>
    <groupId>com.sfi.conflux</groupId>
    <artifactId>conflux-auth-spring-boot-starter</artifactId>
    <version>请替换为实际版本</version>
</dependency>
```

starter 自动装配 `AuthGateway`。24 个内置渠道由 `channels` 模块经 Java SPI 清单自动全量注册，无需逐个声明。state 缓存走 SPI：未注入 `StateCache` Bean 时用默认内存实现（单机）。

### 纯 jar / Spring MVC

引入 `conflux-auth-core` + `conflux-auth-channels`（均零 Spring 依赖）：

```java
AuthConfigProvider provider = new InMemoryAuthConfigProvider()
        .register("demo", "wechat_open",          // configId / channelCode
                  "wx-app-id", "wx-secret",        // clientId / clientSecret
                  "https://your.host/auth/callback", "snsapi_login", // redirectUri / scope
                  null);                           // 平台特有键放 attr（Map，可为 null）
AuthGateway gateway = AuthGateway.builder()
        .configProvider(provider)
        .build();                                  // 内置渠道自动 SPI 注册，无需 .channel()
```

## 2. 配置

配置路径 `conflux.auth.configs.<configId>.<channelCode>`：

```yaml
conflux:
  auth:
    configs:
      demo:
        wechat_open:                       # 渠道编码
          client-id: wx-app-id             # OAuth 公共字段（一等）
          client-secret: wx-secret
          redirect-uri: https://your.host/auth/callback
          scope: snsapi_login
          attr:                            # 平台特有键：私钥/证书/corpId/端点版本等
            token-version: v3              # 键名按各平台约定，框架不写死
```

- **公共字段**：`clientId` / `clientSecret` / `redirectUri` / `scope` 是 `AuthConfig` 一等字段。
- **`attr`（单数）**：平台特有键（私钥、证书路径、corpId、agentId、token 端点版本等）按各平台约定命名，框架不解释其语义。
- 配置源每次读取，凭证变更可重建客户端；接 DB / 配置中心只需自定义 `AuthConfigProvider.getConfig(configId, channelCode)`。

## 3. 授权入口

```java
AuthorizeResult r = authGateway.authorize("demo", "wechat_open");
// 网页形态：r.getAuthorizeUrl()  →  302 跳转
// 扫码形态：r.getQrCodeUrl()     →  渲染二维码
// 小程序/APP：r.getJsApiParams() →  前端调起
```

`AuthorizeOption` 可传平台特有授权参数（`extraParams`）、透传业务上下文（`stateExtra`，回调原样取回）、覆盖 state 过期秒数。框架基类在 `authorize` 时生成 state、写入 `StateCache`，渠道只管把 state 塞进平台参数位。

## 4. 回调换身份

```java
// 1. 暴露回调端点，接收平台回传的原始参数（code/state 等照单全收）
// 2. 按 configId + channelCode 调门面换身份（不回传 state 时渠道已按形态决定是否校验）
AuthResult<AuthUser> result = authGateway.parseCallback(
        "demo", "wechat_open", new AuthCallbackRequest(allParams));
if (result.isSuccess()) {
    AuthUser user = result.getData();
    // user.getOpenId() / getUnionId() / getNickName() ... 各平台归一到同一模型
}
```

- `AuthCallbackRequest` 保留原始参数全量（`param(name)` / `firstParam(names...)` 按名取值），**框架不写死 code/state 的字面字段名**。
- state 校验在渠道模板基类闭环（默认开启、取出即删防重放；容器内免登等不回传 state 的形态由渠道关闭）。
- 门面不验签、不拦截——票据交换与验签由各渠道委托底层 SDK / 标准协议完成。

## 5. state 缓存（分布式场景）

默认 `InMemoryStateCache` 仅适合单机。集群部署时注入自定义 `StateCache` Bean（如 Redis 实现）：

```java
AuthGateway gateway = AuthGateway.builder()
        .configProvider(provider)
        .stateCache(myRedisStateCache)     // save / loadAndRemove
        .build();
```

> 框架**不依赖 Redis**，StateCache 是 SPI 接口，由使用方决定是否引入分布式实现、如何存 state。

## 6. 自定义渠道 / 排错

- 新增平台：实现 `AuthChannel`（或继承 `AbstractAuthChannel`），经 `builder().channel(...)` 声明，同编码覆盖内置。
- `CONFIG_MISSING`：确认 `configId` + `channelCode` 与配置一致（内存实现按 `configId#channelCode` 索引）。
- `ILLEGAL_STATE`：state 缺失、过期或已被使用（重放）；确认回调 URL 与授权入口同源、未丢失 state 参数。
- `xxx_ERROR`（如 `WECHAT_OAUTH_ERROR`）：渠道侧票据交换失败，错误语义由各平台渠道在 `errorCode` 中表达，明细见 `errorMsg`。

> 底层 SDK 版本（wxjava、alipay-sdk 等）在 BOM（`conflux-dependencies`）单源管理。
