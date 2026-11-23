package com.sfi.conflux.pay.channels.mock;

import com.sfi.conflux.pay.api.BillSheet;
import com.sfi.conflux.pay.api.CallbackEvent;
import com.sfi.conflux.pay.api.ChannelCallbackReply;
import com.sfi.conflux.pay.api.ChannelCallbackRequest;
import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.BillHandler;
import com.sfi.conflux.pay.channel.ChannelCallbackHandler;
import com.sfi.conflux.pay.channel.ChannelCode;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.ChannelConfig;
import com.sfi.conflux.pay.channel.ChannelProvider;
import com.sfi.conflux.pay.channel.PaymentHandler;
import com.sfi.conflux.pay.channel.PaymentInvocation;
import com.sfi.conflux.pay.channel.PaymentSnapshot;
import com.sfi.conflux.pay.channel.ProfitShareHandler;
import com.sfi.conflux.pay.channel.ProfitShareInvocation;
import com.sfi.conflux.pay.channel.ProfitShareSnapshot;
import com.sfi.conflux.pay.channel.RefundHandler;
import com.sfi.conflux.pay.channel.RefundInvocation;
import com.sfi.conflux.pay.channel.RefundSnapshot;
import com.sfi.conflux.pay.channel.TransferHandler;
import com.sfi.conflux.pay.channel.TransferInvocation;
import com.sfi.conflux.pay.channel.TransferSnapshot;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import com.sfi.conflux.pay.model.BizOrderType;
import com.sfi.conflux.pay.model.Payment;
import com.sfi.conflux.pay.model.PaymentState;
import com.sfi.conflux.pay.model.ProfitShare;
import com.sfi.conflux.pay.model.ProfitShareState;
import com.sfi.conflux.pay.model.Refund;
import com.sfi.conflux.pay.model.RefundState;
import com.sfi.conflux.pay.model.Transfer;
import com.sfi.conflux.pay.model.TransferState;
import com.sfi.conflux.common.util.JsonUtils;
import com.sfi.conflux.common.util.StringUtils;

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
        methods.add(com.sfi.conflux.pay.channel.PayMethod.MOCK_PAY.getCode());
        methods.add(com.sfi.conflux.pay.channel.PayMethod.MOCK_TRANS.getCode());
        return methods;
    }

    @Override
    public ChannelConfig parseParams(Map<String, String> properties) {
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
            public BillSheet fetchBill(String configId, LocalDate billDate, ChannelConfig config) {
                // 模拟渠道不产出真实账单；对账演示时使用方以内存账单替换本实现
                return new BillSheet()
                        .setConfigId(configId)
                        .setChannelCode(ChannelCode.MOCK)
                        .setBillDate(billDate)
                        .setEntries(new ArrayList<com.sfi.conflux.pay.api.BillEntry>());
            }
        };
    }

    @Override
    public ChannelCallbackHandler callbackHandler() {
        return new ChannelCallbackHandler() {
            @Override
            public CallbackEvent parseCallback(ChannelCallbackRequest raw, ChannelConfig config) {
                String sign = raw.getHeaders() == null ? null : raw.getHeaders().get(SIGN_HEADER);
                MockParams params = params(config);
                if (StringUtils.isBlank(sign) || !sign.equals(params.getAppKey())) {
                    throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "模拟渠道验签失败");
                }
                Map<String, Object> body = JsonUtils.toMap(raw.getBody());
                CallbackEvent event = new CallbackEvent()
                        .setType(BizOrderType.valueOf((String) body.get("type")))
                        .setConfigId(config.getConfigId())
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

    private MockParams params(ChannelConfig config) {
        if (config instanceof MockParams) {
            return (MockParams) config;
        }
        throw new GatewayException(GatewayErrorCode.CONFIG_MISSING,
                "模拟渠道参数类型不匹配: {}", config == null ? "null" : config.getClass().getName());
    }
}
