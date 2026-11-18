# 渠道联调检查清单

真实账号到位后，按本清单逐项验证。除 mock 外的渠道实现均已经过单测与协议结构对齐，
联调目标是验证真实网络下的下单、支付、回调、退款全链路。

## 通用准备

1. 本地起 example（或使用方工程）：`mvn spring-boot:run`，默认 8088 端口
2. 回调地址需要公网可达——本地开发用内网穿透（ngrok/花生壳）暴露 8088，
   回调 URL = `https://<穿透域名>/callback/{渠道编码}?<定位参数>`（微信 appId / 云闪付 merId / PayPal clientId）；
   服务商模式改用 `?accountKey=<应用标识>%23<特约商户号>`，规则见 docs/config-keys.md
3. 无公网时的替代验证：下单后用沙箱买家付款，调 `POST /api/pay/query`
   （内部触发渠道查单补单确认终态）——可验证下单/查单协议，验证不了验签

## 支付宝（沙箱，门槛最低，建议首个联调）

**申请**：支付宝开放平台（open.alipay.com，个人可注册）→ 控制台 → 沙箱环境 →
获取沙箱 AppID、沙箱买家账号；「自定义密钥」工具生成 RSA2 密钥对（应用私钥/支付宝公钥）。

**配置**：

```yaml
conflux.pay:
  apps:
    demo:
      channels:
        alipay:
          notifyUrl: https://<穿透域名>/callback/alipay
          attrs:
            appId: 沙箱AppID（2021xxx）
            privateKey: 应用私钥
            alipayPublicKey: 支付宝公钥
            gateway: https://openapi-sandbox.dl.alipaydev.com/gateway.do
```

**验证路径**：

| 步骤 | 操作 | 预期 |
|---|---|---|
| 扫码下单 | `POST /api/pay`（method=ALI_QR） | 返回 qrCode，`PAYING` |
| 沙箱付款 | 沙箱买家 App 扫码支付 | — |
| 查单确认 | `POST /api/pay/query` | `SUCCEEDED`（查单补单路径） |
| 回调确认 | 内网穿透收 `POST /callback/alipay` | 验签通过、由使用方推进订单状态、应答 `success` |
| 退款 | `POST /api/refund` | 同步 `SUCCEEDED` |
| 关单 | 未支付订单 `POST /api/pay/close` | `CLOSED` |

## PayPal（沙箱）

**申请**：developer.paypal.com（个人可注册）→ Dashboard → Sandbox → Accounts（自动生成
买家/商家测试号）→ Apps & Credentials → Sandbox 应用获取 clientId/clientSecret；Webhook
页可创建 webhook 并拿 webhookId。

**配置**：

```yaml
conflux.pay:
  apps:
    demo:
      channels:
        paypal:
          sandbox: true
          attrs:
            clientId: 沙箱clientId
            clientSecret: 沙箱clientSecret
            webhookId: Webhook订阅ID
```

**验证路径**：

| 步骤 | 操作 | 预期 |
|---|---|---|
| 下单 | `POST /api/pay`（method=PAYPAL_SALE，currency=USD） | approve 链接，`PAYING` |
| 批准 | 沙箱买家登录 approve 链接点击继续 | — |
| 查询捕获 | `POST /api/pay/query` | APPROVED 自动 capture → `SUCCEEDED`，channelOrderNo 变为 capture_id |
| 退款 | `POST /api/refund`（金额≤原单） | `SUCCEEDED` |
| Webhook | 开发者平台配置回调 → 订阅 PAYMENT.CAPTURE.COMPLETED | 验签通过，由使用方推进后续处理 |

## 微信（无 V3 沙箱，需真实商户号）

**申请**：微信支付商户平台（pay.weixin.qq.com，需营业执照）→ 商户号 +
API 安全中心申请 API 证书（拿证书序列号与私钥）+ 设置 APIv3 密钥；公众号/小程序后台关联 AppID。

**配置**：

```yaml
conflux.pay:
  apps:
    demo:
      channels:
        wechat:
          notifyUrl: https://<域名>/callback/wechat
          attrs:
            wxApiVersion: v3
            merchantId: 商户号
            appId: 公众号/小程序AppID
            apiV3Key: APIv3密钥
            merchantSerialNumber: 商户证书序列号
            merchantPrivateKey: "商户私钥 PEM"
```

**验证路径**：JSAPI（channelExtras 传 openid）→ 小程序付款 → 回调验签解密 → `SUCCEEDED`；
Native 扫码；退款（V3 回调）；商家转账；分账（需开通分账产品并先加接收方）。

**V2 存量商户**：`wxApiVersion: v2` + `apiV2Key`，下单/查单/关单/退款走 V2 XML 协议；
企业付款到零钱走 EntPay。

## 云闪付（需企业资质）

**申请**：银联开放平台（open.unionpay.com）商户接入 → 服务商/商户号 + P12 签名证书 +
平台公钥；网关地址生产 `https://partner.95516.com`。

**配置**：

```yaml
conflux.pay:
  apps:
    demo:
      channels:
        unionpay:
          notifyUrl: https://<穿透域名>/callback/unionpay
          attrs:
            merchantId: 商户号
            serProvId: 服务商标识（直连可空）
            p12CertPath: /cert/unionpay.p12
            p12CertPassword: 证书密码
            publicKey: 平台公钥
```

**验证路径**：统一下单（UNION_QR）→ 云闪付 App 扫码付款 → 回调验签 → `SUCCEEDED`；
付款码（UNION_BAR，channelExtras 传 authCode）；退款。

## 常见联调问题排查

| 现象 | 排查点 |
|---|---|
| 验签失败 | 微信：V3 头（Wechatpay-Serial 等）是否被网关改写；支付宝：表单参数是否被二次编码 |
| 回调 404 / 配置缺失 | 回调 URL 查询参数（appId/merId/clientId）是否携带；服务商模式是否带 `accountKey`（`#` 需转义为 `%23`） |
| 启动报 `CONFIG_CONFLICT` | 同一渠道下两个配置生成了相同账户键，为各特约商户配置不同的 `subMchNo` |
| 查单一直 PAYING | 商户号/证书与下单环境是否一致（沙箱 vs 生产） |
| 回调重复到达 | 渠道可能重发同一事件，判重由使用方按业务单号处理（SDK 不判重） |
