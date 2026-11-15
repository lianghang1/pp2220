package io.github.lianghang1.conflux.invoice.channels.piaotong;

/**
 * 票通云（vpiaotong）接口常量，按《票通数电发票接口文档 3.2.0》核对。
 *
 * <p>协议要点：POST JSON 明文信封；业务报文 {@code content} 为
 * {@code Base64(3DES-ECB(password, JSON))}；签名 {@code RSA SHA1withRSA}，
 * 签名串为<b>除 sign 外全部参数按 key 字典序</b> {@code k=v&k=v}；
 * 响应须用平台公钥验签后解密。接口路径后缀均为 {@code .pt}。</p>
 *
 * @author lianghang
 */
public final class PiaoTongSpec {

    private PiaoTongSpec() {
    }

    public static final String CODE = "piaotong";
    public static final String DEFAULT_HOST = "fpkj.vpiaotong.com";
    public static final String DEFAULT_BASE_PATH = "/tp/openapi";
    public static final String DEFAULT_BASE_URL =
            "https://" + DEFAULT_HOST + DEFAULT_BASE_PATH;

    // ---- 业务接口路径（文档编号） ----

    /** 2.4 获取登录短信验证码。 */
    public static final String AUTH_SMS_CODE = "/sendLoginSmsCode.pt";
    /** 2.5 短信登录。 */
    public static final String AUTH_SMS_LOGIN = "/smsLogin.pt";
    /** 2.6 获取实名认证二维码。 */
    public static final String AUTH_QRCODE = "/getAuthenticationQrcode.pt";
    /** 2.7 查询实名认证二维码扫码状态。 */
    public static final String AUTH_QRCODE_SCAN = "/queryAuthQrcodeScanStatus.pt";
    /** 2.8 查询数电账号认证状态。 */
    public static final String AUTH_STATUS = "/getTaxBureauAccountAuthStatus.pt";
    /** 2.9 / 2.23~2.27 开具蓝字数电发票（特定业务以附加字段区分，同一端点）。 */
    public static final String ISSUE = "/invoiceBlue.pt";
    /** 2.11 查询发票主要信息。 */
    public static final String QUERY = "/queryInvoiceInfo.pt";
    /** 2.12 查询发票全票面信息。 */
    public static final String QUERY_FULL = "/queryInvoice.pt";
    /** 2.15 获取数电发票文件（PDF/OFD/XML）。 */
    public static final String FILE = "/getAllEleInvFile.pt";
    /** 2.19 重新发送邮件或短信。 */
    public static final String RESEND = "/resendEmailOrSMS.pt";
    /** 2.38 红字确认单申请（新版：全额/部分、销/购方申请，优先）。 */
    public static final String RED_APPLY_NEW = "/redInvConfirmationApply.pt";
    /** 2.28 红字确认单申请（旧版兜底）。 */
    public static final String RED_APPLY_OLD = "/applySpecialInvRedInfoTable.pt";
    /** 2.29 查看红字发票确认单。 */
    public static final String RED_VIEW = "/querySpecialInvoiceRedInfo.pt";
    /** 2.30 开具红字数电发票。 */
    public static final String RED_ISSUE = "/issueRedSpecialInvoice.pt";
    /** 2.31 红字发票确认单审核（确认/拒绝）。 */
    public static final String RED_AUDIT = "/confirmRedInvConfirmation.pt";
    /** 2.33 从局端下载红字确认单。 */
    public static final String RED_BUREAU = "/downloadSpecialInvRedInfoTable.pt";
    /** 2.34 红字确认单查询（下载）结果。 */
    public static final String RED_RESULT = "/queryRedInfoTableDownloadResult.pt";
    /** 2.45 查询数电账号列表。 */
    public static final String ACCOUNT_LIST = "/listTaxBureauAccount.pt";

    // ---- 信封业务状态码（查询/推送/开票结果通用） ----

    public static final String ST_SUCCESS = "0000";
    /** 未开票。 */
    public static final String ST_NOT_ISSUED = "6666";
    /** 开票中。 */
    public static final String ST_ISSUING = "7777";
    /** 开票失败。 */
    public static final String ST_FAILED = "9999";
    /** 需要扫码或短信认证（响应含 authenticationQrcode/authId）。 */
    public static final String ST_NEED_AUTH = "3999";
    /** 红字确认单申请中（仅红票）。 */
    public static final String ST_RED_APPLYING = "4999";
    /** 红字确认单审核中（仅红票）。 */
    public static final String ST_RED_AUDITING = "5999";

    // ---- 红字确认单 processState ----

    /** 81 无需确认可冲红 / 84 购销双方已确认 / 5 已冲红。 */
    public static final String PS_READY_81 = "81";
    public static final String PS_READY_84 = "84";
    public static final String PS_REDED = "5";
    /** 82 销方录入待购方确认 / 83 购方录入待销方确认 / 0 审核中。 */
    public static final String PS_WAIT_BUYER = "82";
    public static final String PS_WAIT_SELLER = "83";
    public static final String PS_AUDITING = "0";
    /** 4 处理失败 / 7 冲红失败 / 11 撤销失败。 */
    public static final String PS_FAILED_4 = "4";
    public static final String PS_FAILED_7 = "7";
    public static final String PS_FAILED_11 = "11";
    /** 85~90 作废（否认/超时/撤销/异常凭证）。 */
    public static final String PS_VOID_85 = "85";
    public static final String PS_VOID_86 = "86";
    public static final String PS_VOID_87 = "87";
    public static final String PS_VOID_88 = "88";
    public static final String PS_VOID_89 = "89";
    public static final String PS_VOID_90 = "90";

    // ---- 发票种类代码 invoiceIssueKindCode ----

    /** 数电票-增值税专用发票。 */
    public static final String KIND_DIGITAL_SPECIAL = "81";
    /** 数电票-普通发票。 */
    public static final String KIND_DIGITAL_GENERAL = "82";
    /** 增值税电子普通发票（税控）。 */
    public static final String KIND_ELEC_GENERAL = "10";
    /** 增值税电子专用发票（税控）。 */
    public static final String KIND_ELEC_SPECIAL = "08";

    // ---- 其他 ----

    /** 回调推送业务应答 code。 */
    public static final String REPLY_OK = "0000";
    public static final String REPLY_FAIL = "9999";

    /** 红字冲红原因默认值（01 开票有误）。 */
    public static final String DEFAULT_RED_REASON = "01";
}
