# 渠道配置键对照表

配置采用「统一基础键 + 渠道扩展键」两层模型：
语义相同的键全渠道同名（`notifyUrl` / `sandbox` / `subMchNo`），
各渠道凭证体系不同的部分使用渠道自有命名，统一放在 `attrs` 下。
均经 `conflux.pay.apps.<appId>.channels.<渠道>` 声明。

## 统一基础键（全渠道同名）

| 键 | 说明 | 备注 |
|---|---|---|
| notifyUrl | 渠道异步通知地址 | 回调反查定位参数见「账户键与回调路由」 |
| sandbox | 沙箱开关 | 支付宝/PayPal/云闪付有官方沙箱；微信仅 V2 仿真系统；云闪付走自身沙箱地址 |
| subMchNo | 服务商模式特约商户号 | 参与账户键生成；同一渠道应用标识下多个特约商户必须各不相同 |

## 各渠道扩展键（`attrs` 下）

### wechat（wxApiVersion 路由 V2/V3）

| 键 | 必填 | 说明 |
|---|---|---|
| merchantId | ✓（V2/V3 均） | 微信支付商户号 |
| appId | ✓ | 微信应用 AppID |
| apiV3Key | V3 | APIv3 密钥 |
| merchantSerialNumber | V3 | 商户 API 证书序列号 |
| merchantPrivateKey | V3 | 商户 API 私钥 PEM |
| apiV2Key | V2 | 商户支付密钥 |
| entPayKey | 否 | 企业付款密钥（缺省 apiV2Key） |
| appSecret | 否 | 公众号/小程序 AppSecret（仅用户标识 OAuth2 用） |
| subAppId | 否 | 服务商模式特约商户 AppID |
| wxApiVersion | 否 | v2/v3，缺省 v3 |

### alipay

| 键 | 必填 | 说明 |
|---|---|---|
| appId | ✓ | 开放平台应用 AppID |
| privateKey | ✓ | 应用私钥 PEM |
| alipayPublicKey | ✓ | 支付宝公钥（回调验签） |
| signType | 否 | RSA2（缺省）/RSA |
| gateway | 否 | 网关地址（显式配置优先于 sandbox） |
| appAuthToken | 否 | ISV 代商户授权令牌（服务商模式） |

### unionpay

| 键 | 必填 | 说明 |
|---|---|---|
| merchantId | ✓ | 商户号（回调反查键） |
| p12CertPath / p12CertPassword | ✓ | 商户 P12 签名证书 |
| publicKey | ✓ | 平台公钥（回调验签） |
| serProvId | 否 | 服务商标识（直连可空） |

### paypal

| 键 | 必填 | 说明 |
|---|---|---|
| clientId / clientSecret | ✓ | 沙箱/生产应用凭据 |
| webhookId | 否 | Webhook 订阅 ID（回调验签） |

### mock

| 键 | 必填 | 说明 |
|---|---|---|
| appKey | ✓ | 回调验签与配置反查键 |
| payOutcome | 否 | success / fail / paying（缺省） |

## ISV 服务商模式额外键

- 微信：`subMchNo`（统一基础键，特约商户号）+ `attrs.subAppId`；付款码走 V2 直连子商户报文
- 支付宝：`attrs.appAuthToken`（ISV 代商户授权令牌，全接口自动携带）
- 云闪付：`subMchNo`（下单/退款报文携带 merId）

## 账户键与回调路由（多账户 / 服务商模式）

回调处理器必须先取得配置才能验签/解密（报文尚未解开时拿不到其中的商户号），
因此账户只能由**回调地址**定位，不能依赖通知报文内容。

| 概念 | 说明 |
|---|---|
| `accountKey` | 回调反查唯一键，由 `ChannelAccountKey.of(channelAppId, subMchNo)` 生成：普通商户 = `channelAppId`；服务商模式 = `channelAppId#subMchNo` |
| `accountKey` 查询参数 | 回调 URL 显式携带，优先于各渠道原有定位参数（appId / app_id / merId / clientId / appKey） |
| 冲突处理 | 两个配置生成相同账户键时，starter 装载期抛 `CONFIG_CONFLICT` 并拒绝启动，不静默覆盖 |

- **普通商户**（每个账户有独立渠道应用标识）：无需改动，旧回调地址继续用原参数。
- **服务商模式**（同一 appid 挂多个 `subMchNo`）：为每个特约商户配置不同 `subMchNo`，
  回调地址改为 `https://<域名>/callback/{渠道}?accountKey=<应用标识>%23<特约商户号>`。
  `#` 在 URL 中是片段标识符，必须转义为 `%23`，否则服务端收不到完整参数。
- 自定义 `ChannelConfigProvider`（配置在 DB/配置中心）需实现 `getByAccountKey`，
  并在装载期校验账户键唯一性，冲突时抛 `CONFIG_CONFLICT`。

配置示例（同一渠道应用标识、两个特约商户各用一份配置）：

```yaml
conflux.pay:
  apps:
    brand-a:
      channels:
        wechat:
          subMchNo: "1900000109"
          notifyUrl: https://<域名>/callback/wechat?accountKey=wx-isv-appid%231900000109
          attrs:
            merchantId: 服务商商户号
            appId: wx-isv-appid
            apiV3Key: <APIv3密钥>
            merchantSerialNumber: <证书序列号>
            merchantPrivateKey: <商户私钥PEM>
    brand-b:
      channels:
        wechat:
          subMchNo: "1900000110"
          notifyUrl: https://<域名>/callback/wechat?accountKey=wx-isv-appid%231900000110
          attrs:
            merchantId: 服务商商户号
            appId: wx-isv-appid
            apiV3Key: <APIv3密钥>
            merchantSerialNumber: <证书序列号>
            merchantPrivateKey: <商户私钥PEM>
```
