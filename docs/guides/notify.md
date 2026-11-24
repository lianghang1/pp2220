# 通知域使用指南

通知域是统一消息发送门面（已支持短信 + 微信公众号/小程序/企业微信消息全家桶）。门面内部一张
`Map<channelCode, NotificationChannel>` 渠道表，一条 code 只对应一个渠道、路由无歧义；所有消息以统一
`NotificationMessage` 入参、由消息自带 channelCode，门面只有唯一入口 `send(configId, message)`。

短信渠道 `Sms4jChannel` **全量委托 [sms4j](https://github.com/dromara/SMS4J) 原始包**（api/core/provider，不用它的 starter）：配置装载、指纹缓存、线程池与 Factory 注册仍在 `SmsBlendRegistry` 内部。微信渠道（`conflux-notify-channels`）消息 bean 继承 wxjava 原生参数类、委托平台层 `WechatClients` 换取原生 service，不重做 SDK 能力。内置渠道经 SPI 清单在 build 期自动注册，用户零 `.channel()` 即可用；新增渠道不改门面 API。通用契约见 [getting-started.md](../getting-started.md)。

## 1. 引入

### Spring Boot starter（推荐）

```xml
<dependency>
    <groupId>com.sfi.conflux</groupId>
    <artifactId>conflux-notify-spring-boot-starter</artifactId>
    <version>请替换为实际版本</version>
</dependency>
```

starter 自动装配 `NotificationGateway`。sms4j 官方 Factory 装载渠道配置；内置 22 家厂商由 sms4j 自动发现，无需声明。

### 纯 jar / Spring MVC

引入 `conflux-notify-core`（经 core 传递引入 sms4j 原始包），core 零 Spring 依赖：

```java
NotificationConfigProvider provider = new InMemoryNotificationConfigProvider()
        .register("demo", "alibaba",            // configId / channelCode
                  "accessKeyId", "accessKeySecret", "signature", "templateId",
                  null);                         // 厂商特有键放 attr（Map，可为 null）
NotificationGateway gateway = NotificationGateway.builder()
        .configProvider(provider)
        .build();                                // 内置渠道自动发现，无需 .channel()
```

## 2. 配置

配置路径 `conflux.notify.configs.<configId>.<channelCode>`：

```yaml
conflux:
  notify:
    configs:
      demo:
        alibaba:                   # 短信：sms4j 供应商标识
          # 公共凭证四件套（NotificationConfig 一等字段，跨渠道同名）
          access-key-id: xxx
          access-key-secret: xxx
          signature: 你的签名
          template-id: SMS_xxx
          attr:                   # 厂商特有键，键名与 sms4j Config 字段一致
            template-name: code
            region-id: cn-hangzhou
        wechat_mini:               # 微信小程序：一份 appId 覆盖订阅/客服/统一/动态消息
          attr:
            appId: wx...
            secret: ...
        wechat_mp:                 # 微信公众号：模板/客服/订阅/群发
          attr:
            appId: wx...
            secret: ...
        wechat_wecom:              # 企业微信：应用/群聊/互联企业消息
          attr:
            corpId: ww...
            corpSecret: ...
            agentId: "1000002"
```

- **公共凭证四件套**：`accessKeyId` / `accessKeySecret` / `signature` / `templateId` 是短信的一等字段。装载期由 `mergedAttr()` 合并（公共字段优先、attr 兜底），再由 sms4j 官方 Factory 按字段名构建厂商 Config。**不暴露 sms4j 官方配置类**。
- **`channelCode`**：短信直接采用 sms4j 供应商标识（`alibaba` / `tencent` …）；微信按应用形态用 `wechat_mp` / `wechat_mini` / `wechat_wecom`（一套编码与登录域复用，见框架契约 C5）。一份凭证覆盖该应用全部消息类型，消息类型由入参 bean 区分。
- **微信凭证**放 `attr`（appId/secret/corpId/corpSecret/agentId，键名对齐 wxjava），由渠道映射为平台层 `PlatformCredential`；不复用短信四件套。
- 配置源每次读取，凭证变更可重建客户端；接 DB / 配置中心只需自定义 `NotificationConfigProvider`：

```java
NotificationConfigProvider dbProvider = new NotificationConfigProvider() {
    public NotificationConfig getConfig(String configId, String channelCode) { /* 查库 */ ... }
};
NotificationGateway gateway = NotificationGateway.builder().configProvider(dbProvider).build();
```

## 3. 发送

唯一入口 `send(configId, message)`，channelCode 由消息自带：

```java
// 短信：channelCode 写在请求里
NotificationSendRequest sms = new NotificationSendRequest()
        .setChannelCode("alibaba")
        .setBizId("B-1").setPhone("18888888888")
        .setParams(Collections.singletonMap("code", "123456"));
NotificationSendResult r = gateway.send("demo", sms);

// 微信小程序订阅消息：选 bean 决定消息类型，字段用 wxjava 原生 setter
MiniSubscribeMsg msg = new MiniSubscribeMsg();
msg.setToUser("openid");
msg.setTemplateId("tpl_xxx");
NotificationSendResult r2 = gateway.send("demo", msg);   // → wechat_mini 凭证
```

- `NotificationMessage`：统一消息接口，仅 `channelCode()`；短信 `NotificationSendRequest` 与全部微信消息 bean 都实现它。
- 微信消息 bean（`conflux-notify-channels`）：`wechat_mp` 有 `MpTemplateMsg`/`MpKefuMsg`/`MpSubscribeMsg`/`MpMassTagMsg`/`MpMassOpenIdsMsg`/`MpMassPreviewMsg`；`wechat_mini` 有 `MiniSubscribeMsg`/`MiniKefuMsg`/`MiniUniformMsg`/`MiniUpdatableMsg`；`wechat_wecom` 有 `WeComAppMsg`/`WeComAppChatMsg`/`WeComLinkedCorpMsg`，以及群机器人 `WeComRobotTextMsg`/`WeComRobotMarkdownMsg`/`WeComRobotImageMsg`/`WeComRobotFileMsg`/`WeComRobotVoiceMsg`/`WeComRobotNewsMsg`/`WeComRobotCardMsg`。
- 能继承 wxjava 原生参数类的直接继承；群机器人文本/Markdown/图片/文件/语音的原生 API 收散装 String 参数（无 bean 可继承），用自定义 bean 承载字段、渠道内拆参，用户侧用法与其他消息一致。群机器人的 `webhookKey` 随消息 bean 携带，不进凭证配置。
- `NotificationSendResult`：`success`、`bizId`（短信回填）、`channel`（渠道编码）、`configId`、`raw`（SDK 原始返回）、`error`。渠道调用异常归一为失败结果，不击穿调用方。
- **发送即终态**，SDK 不持有台账、不判重、不做频率控制。

## 4. 自定义厂商（sms4j 官方扩展）

内置 22 家渠道开箱即用（标识见 sms4j 官方清单）。接入清单外厂商完全走 [sms4j 官方自定义扩展](https://sms4j.com/doc3/custom.html)：按文档实现 `SmsBlend` / `SupplierConfig` / `BaseProviderFactory` 三个接口，把 Factory 实例交给 `SmsChannelProvider.of(factory)` 声明即可，无需改框架代码：

```java
BaseProviderFactory myFactory = new MyProviderFactory();   // sms4j 官方三接口实现
NotificationGateway gateway = NotificationGateway.builder()
        .configProvider(provider)
        .channel(SmsChannelProvider.of(myFactory))          // 仅自定义渠道需要声明
        .build();
```

运行期也可用 `gateway.registerChannel(provider)` 增补、`gateway.registeredChannels()` 查看已注册渠道。

## 5. 排错

- `CONFIG_MISSING`：确认 `configId` + `channelCode` 与配置一致（内存实现按 `configId#channelCode` 索引）。
- 发送报参数/签名错误：厂商特有键是否用了 sms4j Config 的真实字段名（放 `attr`）；公共四件套是否齐全。
- `raw` 里有原始错误：查 sms4j 对应厂商的返回码含义，SDK 不改写第三方错误。
- 自定义厂商不生效：确认 Factory 已通过 `SmsChannelProvider.of(factory)` 声明，且 `configId` + `channelCode` 匹配。

> sms4j 版本在 BOM（`conflux-dependencies`）单源管理。
