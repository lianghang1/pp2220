# 渠道配置键对照表

配置采用「统一基础键 + 渠道扩展键」两层模型（与 x-file-storage 的配置风格一致）：
语义相同的键全渠道同名（`appId` / `notifyUrl` / `sandbox` / `privateKey`），
各渠道凭证体系不同的部分使用渠道自有命名。均经 `pay-gateway.apps.<appId>.channels.<渠道>` 声明。

## 统一基础键（全渠道同名）

| 键 | 说明 | 备注 |
|---|---|---|
| appId | 渠道侧应用/身份标识 | 微信=AppID、支付宝=AppID、PayPal=ClientId、云闪付无此概念（用 merchantId） |
| notifyUrl | 渠道异步通知地址 | 回调反查定位参数见「回调配置反查」章节 |
| sandbox | 沙箱开关 | 支付宝/PayPal/云闪付有官方沙箱；微信仅 V2 仿真系统；云闪付走自身沙箱地址 |

## 各渠道扩展键

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

- 微信：ChannelConfig 的 `subMchNo`（特约商户号）+ 渠道参数 `subAppId`；付款码走 V2 直连子商户报文
- 支付宝：渠道参数 `appAuthToken`（ISV 代商户授权令牌，全接口自动携带）
- 云闪付：ChannelConfig 的 `subMchNo`（下单/退款报文携带 merId）

声明式配置示例：

```yaml
pay-gateway:
  apps:
    app-001:
      channels:
        wechat:
          merchantId: 服务商商户号
          subMchNo: 特约商户号     # 服务商模式
          subAppId: 特约商户AppID
          ...
```
