# 渠道配置键对照表

配置采用「公共字段 + 渠道个性 `attr`」模型：
公共字段（`configId` / `channelCode` / `notifyUrl` / `sandbox`）为配置对象一等字段，
各渠道凭证体系不同的部分使用渠道自有命名，统一放在 `attr` 下。
均经 `conflux.pay.configs.<configId>.<channelCode>` 声明。

> 公共字段优先：公共字段与 `attr` 出现同名键时，以公共字段为准；同一语义字段只存一处。

## 公共字段（全渠道同名，一等字段）

| 键 | 说明 | 备注 |
|---|---|---|
| notifyUrl | 渠道异步通知地址 | 业务也可在下单/退款/转账请求里用 `.setNotifyUrl(...)` 按单覆盖（请求优先、配置兜底） |
| sandbox | 沙箱开关 | 支付宝/PayPal/云闪付有官方沙箱；微信仅 V2 仿真系统；天满走自身测试地址 |

## 回调配置定位

验签仍在框架，但配置定位权在使用方：业务按自己的订单号查库得到 `configId`（发票再加
`sellerTaxNo`），显式调用 `parseCallback(configId, request)`，框架据此取配置验签。
框架不再从回调报文隐式反查账户键（accountKey 整套已删除），回调 URL 无需携带任何账户定位参数。

## 各渠道扩展键（`attr` 下）

> 服务商模式特约商户号 `subMchNo` 也放 `attr`（键名 `subMchNo`），不再作为公共字段。

### wechat（wxApiVersion 路由 V2/V3）

| 键 | 必填 | 说明 |
|---|---|---|
| merchantId | ✓（V2/V3 均） | 微信支付商户号 |
| appId | ✓ | 微信应用 AppID（渠道真实 appid，不受框架 configId 改名影响） |
| apiV3Key | V3 | APIv3 密钥 |
| merchantSerialNumber | V3 | 商户 API 证书序列号 |
| merchantPrivateKey | V3 | 商户 API 私钥 PEM |
| apiV2Key | V2 | 商户支付密钥 |
| entPayKey | 否 | 企业付款密钥（缺省 apiV2Key） |
| appSecret | 否 | 公众号/小程序 AppSecret（仅用户标识 OAuth2 用） |
| subAppId | 否 | 服务商模式特约商户 AppID |
| subMchNo | 否 | 服务商模式特约商户号（下沉自公共字段） |
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
| merchantId | ✓ | 商户号 |
| p12CertPath / p12CertPassword | ✓ | 商户 P12 签名证书 |
| publicKey | ✓ | 平台公钥（回调验签） |
| serProvId | 否 | 服务商标识（直连可空） |
| subMchNo | 否 | 服务商模式特约商户号 |

### paypal

| 键 | 必填 | 说明 |
|---|---|---|
| clientId / clientSecret | ✓ | 沙箱/生产应用凭据 |
| webhookId | 否 | Webhook 订阅 ID（回调验签） |

### chinaums（银联商务天满）

> 认证采用 OPEN-BODY-SIG（无状态逐请求签名），回调采用「触发主动查单确认」，
> 详见 [支付域使用指南](../guides/pay.md)。

| 键 | 必填 | 说明 |
|---|---|---|
| appId | ✓ | 天满产品 ID |
| appKey | ✓ | 天满产品密钥 |
| mid | ✓ | 商户号 |
| tid | ✓ | 终端号 |
| subAppId | 否 | 微信开放平台 appId（微信渠道下单必需） |
| gateway | 否 | 自定义网关前缀；缺省按 sandbox 切正式/测试地址 |

### mock

| 键 | 必填 | 说明 |
|---|---|---|
| appKey | ✓ | 验签凭据 |
| payOutcome | 否 | success / fail / paying（缺省） |

## 服务商模式配置示例

同一渠道、不同业务用不同 `configId` 各自一份配置；`subMchNo` 下沉进 `attr`：

```yaml
conflux.pay:
  configs:
    brand-a:
      wechat:
        notifyUrl: https://<域名>/callback/wechat
        attr:
          merchantId: 服务商商户号
          appId: wx-isv-appid
          subMchNo: "1900000109"
          apiV3Key: <APIv3密钥>
          merchantSerialNumber: <证书序列号>
          merchantPrivateKey: <商户私钥PEM>
    brand-b:
      wechat:
        notifyUrl: https://<域名>/callback/wechat
        attr:
          merchantId: 服务商商户号
          appId: wx-isv-appid
          subMchNo: "1900000110"
          apiV3Key: <APIv3密钥>
          merchantSerialNumber: <证书序列号>
          merchantPrivateKey: <商户私钥PEM>
```

回调时业务按订单查库得到对应 `configId`（如 `brand-a`），调用 `parseCallback(configId, request)` 即可。
