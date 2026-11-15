package io.github.lianghang1.conflux.invoice.channels.baiwangjs;

/**
 * 百旺金穗云「税务共享销项」接口规格常量。
 *
 * <p><b>文档依据</b>：语雀 {@code baiwangqiyuanyuan/swgx}（用户指定为准），
 * 与 {@code baiwangjs.apifox.cn} 为同一套「百旺金穗云-SaaS 标准化接口」规范的两个发布渠道。
 * 接口路径、字段、状态码均取自公开文档，未核对的一律不编造。</p>
 *
 * <p><b>版本口径</b>：同一能力同时存在 V1 / V2 时默认走 V2，V1 保留完整实现，
 * 由 {@code attrs.apiVersion=V1} 切换。报文组装差异见 {@link BaiwangJsCodec}：
 * V1 用合并字段 {@code xsfDzdh/xsfYhzh/gmfDzdh/gmfYhzh}（地址与电话以 {@code ~~} 分隔），
 * V2 用拆分字段 {@code xsfDz/xsfDh/xsfYh/xsfZh/gmfDz/gmfDh/gmfYh/gmfZh}。</p>
 *
 * <p><b>网关签名</b>：{@code Base64(HmacSHA256(sign_str, SECRET))}，sign_str 有严格顺序，
 * request-line 使用完整 URL path（含 {@link #GATEWAY_PATH_PREFIX}），Date 为 GMT/两位日期。</p>
 *
 * @author lianghang
 */
public final class BaiwangJsSpec {

    public static final String CODE = "baiwangjs";
    public static final String DISPLAY_NAME = "百旺金穗云";

    /** 正式网关地址（host）。 */
    public static final String DEFAULT_HOST = "api.baiwangjs.com";
    /** 网关应用前缀；baseUrl = https://host + 本前缀，签名 request-line 用完整 path。 */
    public static final String GATEWAY_PATH_PREFIX =
            "/swgx-saas/agentinvoiceservice-cloudservice-cloudservice/agentiscloud";
    public static final String DEFAULT_BASE_URL = "https://" + DEFAULT_HOST + GATEWAY_PATH_PREFIX;

    /** 默认 UA（文档示例 {@code Java 1.8.0_181 64 bit, sdk-ver=1.0.0}）。 */
    public static final String DEFAULT_UA = "Java 1.8.0 64 bit, sdk-ver=1.0.0";

    // ---- 接口路径（全部来自公开文档） ----

    /** 3.2.6(V2) 发票开具申请（默认）。 */
    public static final String ISSUE_V2 = "/cloud/v2/applyInvoice";
    /** 3.2.6 发票开具申请（V1，保留实现）。 */
    public static final String ISSUE_V1 = "/cloud/applyInvoice";
    /** 3.2.7(V2) 发票查询-按订单流水号查询（默认）。 */
    public static final String QUERY_V2 = "/cloud/v2/queryOrder";
    /** 3.2.7 发票查询-按订单流水号查询（V1，保留实现）。 */
    public static final String QUERY_V1 = "/cloud/queryOrder";
    /** 3.1.1 查询企业唯一标识（获取 qyId，仅需一次）。 */
    public static final String QUERY_UNIQUE_SIGN = "/cloud/queryUniqueSign";
    /** 3.3.3 数电红冲申请。 */
    public static final String RED_APPLY = "/cloud/applyElecRedInvoice";
    /** 3.3.4 数电红冲结果查询。 */
    public static final String RED_QUERY = "/cloud/queryRedInvRes";
    /** 3.4.2 电子税务局登录（两步短信）。 */
    public static final String LOGIN = "/cloud/dzswjLogin";
    /** 3.4.4 获取实人认证二维码（同步）。 */
    public static final String QRCODE_SYNC = "/elecEwm/queryEwmrzSnyc";
    /** 3.4.3 获取实人认证二维码（异步）。 */
    public static final String QRCODE_ASYNC = "/elecEwm/queryEwmrz";
    /** 3.4.5 认证结果查询（异步返回 cxnsrfx）。 */
    public static final String RISK_INFO = "/elecEwm/queryRiskInfo";

    // ---- 回调业务类型 ywlxdm ----

    /** 开票结果（含红字开具结果，红字带 yqdfphm 原票号）。 */
    public static final String YW_FPKJ = "fpkj";
    /** 版式文件（xmlurl/ofdurl/pdf/jfdz）。 */
    public static final String YW_QDBSWJ = "qdbswj";
    /** 红字申请单结果。 */
    public static final String YW_QDHZSQ = "qdhzsq";
    /** 实人认证二维码。 */
    public static final String YW_EWMRZ = "ewmrz";
    /** 认证结果/风险信息。 */
    public static final String YW_CXNSRFX = "cxnsrfx";

    // ---- 回调 status ----

    public static final String ST_SUCCESS = "1";
    public static final String ST_FAILED = "2";
    /** 已开具未生成 PDF（税控电子票）：继续等待版式。 */
    public static final String ST_NO_PDF = "3";
    /** 单据删除。 */
    public static final String ST_DELETED = "6";
    /** 需要登录电子税务局。 */
    public static final String ST_NEED_LOGIN = "1004";
    /** 需要实人认证（扫脸）。 */
    public static final String ST_NEED_AUTH = "1003";

    // ---- 同步响应 code ----

    /** 受理成功 / 登录成功。 */
    public static final String CODE_OK = "0";
    /** 短信验证码已发送，需带 sms 二次请求（3.4.2）。 */
    public static final String CODE_SMS_SENT = "2001";

    // ---- 票种 fplxdm 默认映射 ----

    public static final String FPLXDM_DIGITAL_SPECIAL = "01";
    public static final String FPLXDM_DIGITAL_GENERAL = "02";
    public static final String FPLXDM_VEHICLE = "03";
    public static final String FPLXDM_VAT_SPECIAL = "004";
    public static final String FPLXDM_VAT_GENERAL = "007";

    /** 回调地址上用于反查账户配置的查询参数名（框架统一约定）。 */
    public static final String ACCOUNT_KEY_PARAM = "accountKey";

    private BaiwangJsSpec() {
    }
}
