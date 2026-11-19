package io.github.lianghang1.conflux.invoice.channels.nuonuo;

/**
 * 诺诺开放平台接口规格常量。
 *
 * <p><b>文档依据</b>：诺诺开放平台公开 API 文档（{@code jss.com.cn/open}），
 * method 名、状态码、返回字段均取自公开文档与官方 SDK 示例，未核对的一律不编造。</p>
 *
 * <p><b>协议</b>：统一网关 {@code /open/v1/services}，请求头 {@code method} 指定接口，
 * 业务参数打包成 JSON 字符串由官方 {@code NNOpenSDK#sendPostSyncRequest} 组装签名并发送；
 * 渠道侧不自行实现签名。</p>
 *
 * @author lianghang
 */
public final class NuonuoSpec {

    public static final String CODE = "nuonuo";
    public static final String DISPLAY_NAME = "诺诺网";

    /** 正式网关地址（SDK sendPostSyncRequest 的 url 入参）。 */
    public static final String DEFAULT_GATEWAY = "https://sdk.nuonuo.com/open/v1/services";
    /** 沙箱网关地址。 */
    public static final String SANDBOX_GATEWAY = "https://sandbox.nuonuocs.cn/open/v1/services";

    // ---- 业务接口 method（公开文档） ----

    /** 诺税通 SaaS 开票（V2.0）。返回 result.invoiceSerialNum。 */
    public static final String METHOD_REQUEST_BILLING = "nuonuo.OpeMplatform.requestBillingNew";
    /** 开票/发票结果查询（V2.0）。入参 serialNos/orderNos 数组，返回 result 数组。 */
    public static final String METHOD_QUERY_INVOICE = "nuonuo.OpeMplatform.queryInvoiceResult";

    // ---- 同步响应 code ----

    /** 开票受理成功（开票接口）。 */
    public static final String CODE_BILLING_OK = "E0000";
    /** 通用成功码（查询/其他接口）。 */
    public static final String CODE_OK = "H200";

    // ---- 查单 status（queryInvoiceResult 返回 result[].status） ----
    // 2=开票完成(最终态) 20=开票中 21=开票成功签章中 22=开票失败

    /** 查单：开票完成（最终态）。 */
    public static final String QRY_SUCCESS = "2";
    /** 查单：开票中。 */
    public static final String QRY_PROCESSING = "20";
    /** 查单：开票成功签章中。 */
    public static final String QRY_SIGNING = "21";
    /** 查单：开票失败。 */
    public static final String QRY_FAILED = "22";

    // ---- 开票异步通知 status ----
    // 1=开票完成 2=开票失败 3=开票成功签章失败（电票）

    /** 回调：开票完成。 */
    public static final String NOTICE_SUCCESS = "1";
    /** 回调：开票失败。 */
    public static final String NOTICE_FAILED = "2";
    /** 回调：开票成功签章失败。 */
    public static final String NOTICE_SIGN_FAILED = "3";

    /** 开票结果通知 action。 */
    public static final String NOTICE_ACTION = "nuonuo_billing_new_notice";

    /** 回调地址上用于反查账户配置的查询参数名（框架统一约定）。 */
    public static final String ACCOUNT_KEY_PARAM = "accountKey";

    /** 回调成功应答（文档示例：返回其他内容会触发重推）。 */
    public static final String REPLY_SUCCESS = "success";

    private NuonuoSpec() {
    }
}
