package io.github.lianghang1.conflux.pay.channels.mock;

import io.github.lianghang1.conflux.pay.api.BillSheet;
import io.github.lianghang1.conflux.pay.api.CallbackEvent;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackReply;
import io.github.lianghang1.conflux.pay.api.ChannelCallbackRequest;
import io.github.lianghang1.conflux.pay.api.PayPayload;
import io.github.lianghang1.conflux.pay.channel.BillHandler;
import io.github.lianghang1.conflux.pay.channel.CallbackConfigLookup;
import io.github.lianghang1.conflux.pay.channel.ChannelCallbackHandler;
import io.github.lianghang1.conflux.pay.channel.ChannelCode;
import io.github.lianghang1.conflux.pay.channel.ChannelAccountKey;
import io.github.lianghang1.conflux.pay.channel.ChannelConfig;
import io.github.lianghang1.conflux.pay.channel.ChannelParams;
import io.github.lianghang1.conflux.pay.channel.ChannelProvider;
import io.github.lianghang1.conflux.pay.channel.PaymentHandler;
import io.github.lianghang1.conflux.pay.channel.PaymentInvocation;
import io.github.lianghang1.conflux.pay.channel.PaymentSnapshot;
import io.github.lianghang1.conflux.pay.channel.ProfitShareHandler;
import io.github.lianghang1.conflux.pay.channel.ProfitShareInvocation;
import io.github.lianghang1.conflux.pay.channel.ProfitShareSnapshot;
import io.github.lianghang1.conflux.pay.channel.RefundHandler;
import io.github.lianghang1.conflux.pay.channel.RefundInvocation;
import io.github.lianghang1.conflux.pay.channel.RefundSnapshot;
import io.github.lianghang1.conflux.pay.channel.TransferHandler;
import io.github.lianghang1.conflux.pay.channel.TransferInvocation;
import io.github.lianghang1.conflux.pay.channel.TransferSnapshot;
import io.github.lianghang1.conflux.pay.exception.GatewayErrorCode;
import io.github.lianghang1.conflux.common.exception.GatewayException;
import io.github.lianghang1.conflux.pay.model.BizOrderType;
import io.github.lianghang1.conflux.pay.model.Payment;
import io.github.lianghang1.conflux.pay.model.PaymentState;
import io.github.lianghang1.conflux.pay.model.ProfitShare;
import io.github.lianghang1.conflux.pay.model.ProfitShareState;
import io.github.lianghang1.conflux.pay.model.Refund;
import io.github.lianghang1.conflux.pay.model.RefundState;
import io.github.lianghang1.conflux.pay.model.Transfer;
import io.github.lianghang1.conflux.pay.model.TransferState;
import io.github.lianghang1.conflux.common.util.JsonUtils;
import io.github.lianghang1.conflux.common.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟渠道：零三方依赖，用于端到端演示与集成测试。
 *
 * <p>行为约定：
 * ① 下单按参数 {@code payOutcome} 返回 paying / success / fail（fail 抛渠道异常）；
 * ② 退款/分账同步返回成功，转账返回处理中（等待回调）；
 * ③ 回调报文为 JSON（type/bizOrderNo/channelOrderNo/success/amount/occurTime），
 * 验签校验请求头 {@code X-Mock-Sign} 必须等于 {@code appKey}；
 * ④ 回调成功应答 {@code success}，失败应答 {@code fail}。</p>
 *
 * @author lianghang
 */
public class MockChannelProvider implements ChannelProvider {

    /** 回调验签请求头名 */
    public static final String SIGN_HEADER = "X-Mock-Sign";

    @Override
    public String channelCode() {
        return ChannelCode.MOCK;
    }

    @Override
    public Set<String> supportedMethods() {
        Set<String> methods = new HashSet<String>();
        methods.add(io.github.lianghang1.conflux.pay.channel.PayMethod.MOCK_PAY.getCode());
        methods.add(io.github.lianghang1.conflux.pay.channel.PayMethod.MOCK_TRANS.getCode());
        return methods;
    }

    @Override
    public ChannelParams parseParams(Map<String, String> properties) {
        return MockParams.fromMap(properties);
    }

    @Override
    public PaymentHandler paymentHandler() {
        return new PaymentHandler() {
            @Override
            public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
                MockParams params = params(config);
                if (MockParams.OUTCOME_FAIL.equals(params.getPayOutcome())) {
                    throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED, "模拟渠道下单失败");
                }
                if (MockParams.OUTCOME_SUCCESS.equals(params.getPayOutcome())) {
                    return new PaymentInvocation()
                            .setChannelOrderNo("MOCK-" + payment.getBizOrderNo())
                            .setPayload(PayPayload.none())
                            .setState(PaymentState.SUCCEEDED);
                }
                return new PaymentInvocation()
                        .setChannelOrderNo("MOCK-" + payment.getBizOrderNo())
                        .setPayload(PayPayload.qrCode("mock-qr://" + payment.getBizOrderNo()))
                        .setState(PaymentState.PAYING);
            }

            @Override
            public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
                return new PaymentSnapshot()
                        .setChannelOrderNo("MOCK-" + payment.getBizOrderNo())
                        .setState(payment.getState())
                        .setPaidAmount(payment.getState().isPaid() ? payment.getAmount() : null)
                        .setSucceedTime(payment.getSucceedTime())
                        .setRawMsg("mock");
            }

            @Override
            public void closePayment(Payment payment, ChannelConfig config) {
                // 模拟渠道关单无副作用
            }
        };
    }

    @Override
    public RefundHandler refundHandler() {
        return new RefundHandler() {
            @Override
            public RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config) {
                return new RefundInvocation()
                        .setChannelRefundNo("MOCKR-" + refund.getBizRefundNo())
                        .setState(RefundState.SUCCEEDED);
            }

            @Override
            public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
                return new RefundSnapshot()
                        .setChannelRefundNo("MOCKR-" + refund.getBizRefundNo())
                        .setState(refund.getState())
                        .setSucceedTime(refund.getSucceedTime())
                        .setRawMsg("mock");
            }
        };
    }

    @Override
    public TransferHandler transferHandler() {
        return new TransferHandler() {
            @Override
            public TransferInvocation createTransfer(Transfer transfer, ChannelConfig config) {
                return new TransferInvocation()
                        .setChannelTransferNo("MOCKT-" + transfer.getBizTransferNo())
                        .setState(TransferState.PROCESSING);
            }

            @Override
            public TransferSnapshot queryTransfer(Transfer transfer, ChannelConfig config) {
                return new TransferSnapshot()
                        .setChannelTransferNo("MOCKT-" + transfer.getBizTransferNo())
                        .setState(transfer.getState())
                        .setSucceedTime(transfer.getSucceedTime())
                        .setRawMsg("mock");
            }
        };
    }

    @Override
    public ProfitShareHandler profitShareHandler() {
        return new ProfitShareHandler() {
            @Override
            public ProfitShareInvocation createProfitShare(ProfitShare share, ChannelConfig config) {
                return new ProfitShareInvocation()
                        .setChannelShareNo("MOCKP-" + share.getBizShareNo())
                        .setState(ProfitShareState.FINISHED);
            }

            @Override
            public ProfitShareSnapshot queryProfitShare(ProfitShare share, ChannelConfig config) {
                return new ProfitShareSnapshot()
                        .setChannelShareNo("MOCKP-" + share.getBizShareNo())
                        .setState(share.getState())
                        .setFinishTime(share.getFinishTime())
                        .setRawMsg("mock");
            }
        };
    }

    @Override
    public BillHandler billHandler() {
        return new BillHandler() {
            @Override
            public BillSheet fetchBill(String appId, LocalDate billDate, ChannelConfig config) {
                // 模拟渠道不产出真实账单；对账演示时使用方以内存账单替换本实现
                return new BillSheet()
                        .setAppId(appId)
                        .setChannelCode(ChannelCode.MOCK)
                        .setBillDate(billDate)
                        .setEntries(new ArrayList<io.github.lianghang1.conflux.pay.api.BillEntry>());
            }
        };
    }

    @Override
    public ChannelCallbackHandler callbackHandler() {
        return new ChannelCallbackHandler() {
            @Override
            public CallbackEvent parseCallback(ChannelCallbackRequest raw, CallbackConfigLookup configLookup) {
                String sign = raw.getHeaders() == null ? null : raw.getHeaders().get(SIGN_HEADER);
                ChannelConfig config = configLookup.byAccountKey(
                        ChannelAccountKey.resolve(raw, appKeyOf(raw)));
                MockParams params = params(config);
                if (StringUtils.isBlank(sign) || !sign.equals(params.getAppKey())) {
                    throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "模拟渠道验签失败");
                }
                Map<String, Object> body = JsonUtils.toMap(raw.getBody());
                CallbackEvent event = new CallbackEvent()
                        .setType(BizOrderType.valueOf((String) body.get("type")))
                        .setAppId(config.getAppId())
                        .setBizOrderNo((String) body.get("bizOrderNo"))
                        .setChannelOrderNo((String) body.get("channelOrderNo"))
                        .setSuccess(Boolean.TRUE.equals(body.get("success")))
                        .setAmount(body.get("amount") == null ? null
                                : Long.valueOf(String.valueOf(body.get("amount"))));
                String occurTime = (String) body.get("occurTime");
                if (StringUtils.isNotBlank(occurTime)) {
                    event.setOccurTime(LocalDateTime.parse(occurTime.replace(' ', 'T')));
                }
                return event;
            }

            @Override
            public String successReply() {
                return "success";
            }

            @Override
            public String failureReply() {
                return "fail";
            }
        };
    }

    /**
     * 从回调请求中提取渠道侧应用标识：优先取报文 {@code appKey} 字段，
     * 缺省回落到默认 appKey（单应用场景足够）。
     */
    private String appKeyOf(ChannelCallbackRequest raw) {
        Map<String, Object> body = JsonUtils.toMap(raw.getBody());
        Object appKey = body.get("appKey");
        return appKey == null ? new MockParams().getAppKey() : String.valueOf(appKey);
    }

    private MockParams params(ChannelConfig config) {
        if (config.getParams() instanceof MockParams) {
            return (MockParams) config.getParams();
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "模拟渠道参数类型不匹配: {}", config.getParams() == null ? "null" : config.getParams().getClass().getName());
    }
}
