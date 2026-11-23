package com.sfi.conflux.pay.support;

import com.sfi.conflux.pay.api.BillEntry;
import com.sfi.conflux.pay.api.BillSheet;
import com.sfi.conflux.pay.api.CallbackEvent;
import com.sfi.conflux.pay.api.ChannelCallbackRequest;
import com.sfi.conflux.pay.api.PayPayload;
import com.sfi.conflux.pay.channel.BillHandler;
import com.sfi.conflux.pay.channel.ChannelCallbackHandler;
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
import com.sfi.conflux.pay.config.ChannelConfigProvider;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 测试用模拟渠道：以内存状态模拟渠道侧行为，覆盖全部六项能力。
 *
 * <p>行为要点：下单返回二维码唤起参数与 PAYING 状态（可注入故障）；
 * 渠道侧订单状态由测试用例直接操控（模拟渠道侧的支付完成）；
 * 回调验签校验 {@code X-Test-Sign} 请求头，报文为 JSON。
 * 支付/转账方式（TEST_PAY/TEST_TRANS）由 {@code GatewayTestHarness} 的静态块注册。</p>
 *
 * @author lianghang
 */
public class TestChannelProvider implements ChannelProvider {

    public static final String CODE = "test-channel";
    public static final String APP_KEY = "test-app-key";
    public static final String METHOD_PAY = "TEST_PAY";
    public static final String METHOD_TRANS = "TEST_TRANS";
    public static final String VALID_SIGN = "valid-sign";

    /** 渠道侧支付单状态（bizOrderNo → 状态），由测试用例操控以模拟补单/查单。 */
    public final Map<String, PaymentState> channelPaymentStates = new ConcurrentHashMap<String, PaymentState>();
    /** 渠道侧退款单状态（bizRefundNo → 状态）。 */
    public final Map<String, RefundState> channelRefundStates = new ConcurrentHashMap<String, RefundState>();
    /** 渠道侧转账单状态（bizTransferNo → 状态）。 */
    public final Map<String, TransferState> channelTransferStates = new ConcurrentHashMap<String, TransferState>();
    /** 渠道侧分账单状态（bizShareNo → 状态）。 */
    public final Map<String, ProfitShareState> channelShareStates = new ConcurrentHashMap<String, ProfitShareState>();
    /** 渠道侧账单条目（测试用例预置）。 */
    public final List<BillEntry> billEntries = new ArrayList<BillEntry>();
    /** 下次下单是否注入故障。 */
    public final AtomicBoolean failNextCreate = new AtomicBoolean(false);

    @Override
    public String channelCode() {
        return CODE;
    }

    @Override
    public Set<String> supportedMethods() {
        Set<String> methods = new HashSet<String>();
        methods.add(METHOD_PAY);
        methods.add(METHOD_TRANS);
        return methods;
    }

    @Override
    public PaymentHandler paymentHandler() {
        return new PaymentHandler() {
            @Override
            public PaymentInvocation createPayment(Payment payment, ChannelConfig config) {
                if (failNextCreate.compareAndSet(true, false)) {
                    throw new GatewayException(GatewayErrorCode.CHANNEL_CALL_FAILED, "模拟渠道下单故障");
                }
                channelPaymentStates.put(payment.getBizOrderNo(), PaymentState.PAYING);
                return new PaymentInvocation()
                        .setChannelOrderNo("CH-" + payment.getBizOrderNo())
                        .setPayload(PayPayload.qrCode("test-qr://" + payment.getBizOrderNo()))
                        .setState(PaymentState.PAYING);
            }

            @Override
            public PaymentSnapshot queryPayment(Payment payment, ChannelConfig config) {
                PaymentState state = channelPaymentStates.getOrDefault(payment.getBizOrderNo(), PaymentState.PAYING);
                return new PaymentSnapshot()
                        .setChannelOrderNo("CH-" + payment.getBizOrderNo())
                        .setState(state)
                        .setPaidAmount(state == PaymentState.SUCCEEDED ? payment.getAmount() : null)
                        .setSucceedTime(state == PaymentState.SUCCEEDED ? LocalDateTime.now() : null)
                        .setRawMsg(state.name());
            }

            @Override
            public void closePayment(Payment payment, ChannelConfig config) {
                PaymentState current = channelPaymentStates.get(payment.getBizOrderNo());
                if (current == PaymentState.SUCCEEDED) {
                    throw new GatewayException(com.sfi.conflux.pay.exception.GatewayErrorCode.STATE_ILLEGAL,
                            "渠道侧订单已支付，不允许关单");
                }
                channelPaymentStates.put(payment.getBizOrderNo(), PaymentState.CLOSED);
            }
        };
    }

    @Override
    public RefundHandler refundHandler() {
        return new RefundHandler() {
            @Override
            public RefundInvocation createRefund(Refund refund, Payment payment, ChannelConfig config) {
                channelRefundStates.put(refund.getBizRefundNo(), RefundState.SUCCEEDED);
                return new RefundInvocation()
                        .setChannelRefundNo("CHR-" + refund.getBizRefundNo())
                        .setState(RefundState.SUCCEEDED);
            }

            @Override
            public RefundSnapshot queryRefund(Refund refund, ChannelConfig config) {
                RefundState state = channelRefundStates.getOrDefault(refund.getBizRefundNo(), RefundState.PROCESSING);
                return new RefundSnapshot()
                        .setChannelRefundNo("CHR-" + refund.getBizRefundNo())
                        .setState(state)
                        .setSucceedTime(state == RefundState.SUCCEEDED ? LocalDateTime.now() : null)
                        .setRawMsg(state.name());
            }
        };
    }

    @Override
    public TransferHandler transferHandler() {
        return new TransferHandler() {
            @Override
            public TransferInvocation createTransfer(Transfer transfer, ChannelConfig config) {
                channelTransferStates.put(transfer.getBizTransferNo(), TransferState.PROCESSING);
                return new TransferInvocation()
                        .setChannelTransferNo("CHT-" + transfer.getBizTransferNo())
                        .setState(TransferState.PROCESSING);
            }

            @Override
            public TransferSnapshot queryTransfer(Transfer transfer, ChannelConfig config) {
                TransferState state = channelTransferStates.getOrDefault(transfer.getBizTransferNo(), TransferState.PROCESSING);
                return new TransferSnapshot()
                        .setChannelTransferNo("CHT-" + transfer.getBizTransferNo())
                        .setState(state)
                        .setSucceedTime(state == TransferState.SUCCEEDED ? LocalDateTime.now() : null)
                        .setRawMsg(state.name());
            }
        };
    }

    @Override
    public ProfitShareHandler profitShareHandler() {
        return new ProfitShareHandler() {
            @Override
            public ProfitShareInvocation createProfitShare(ProfitShare share, ChannelConfig config) {
                channelShareStates.put(share.getBizShareNo(), ProfitShareState.FINISHED);
                return new ProfitShareInvocation()
                        .setChannelShareNo("CHP-" + share.getBizShareNo())
                        .setState(ProfitShareState.FINISHED);
            }

            @Override
            public ProfitShareSnapshot queryProfitShare(ProfitShare share, ChannelConfig config) {
                ProfitShareState state = channelShareStates.getOrDefault(share.getBizShareNo(), ProfitShareState.PROCESSING);
                return new ProfitShareSnapshot()
                        .setChannelShareNo("CHP-" + share.getBizShareNo())
                        .setState(state)
                        .setFinishTime(state == ProfitShareState.FINISHED ? LocalDateTime.now() : null)
                        .setRawMsg(state.name());
            }
        };
    }

    @Override
    public BillHandler billHandler() {
        return new BillHandler() {
            @Override
            public BillSheet fetchBill(String configId, LocalDate billDate, ChannelConfig config) {
                return new BillSheet()
                        .setConfigId(configId)
                        .setChannelCode(CODE)
                        .setBillDate(billDate)
                        .setEntries(new ArrayList<BillEntry>(billEntries));
            }
        };
    }

    @Override
    public ChannelCallbackHandler callbackHandler() {
        return new ChannelCallbackHandler() {
            @Override
            public CallbackEvent parseCallback(ChannelCallbackRequest raw, ChannelConfig config) {
                String sign = raw.getHeaders() == null ? null : raw.getHeaders().get("X-Test-Sign");
                if (!VALID_SIGN.equals(sign)) {
                    throw new GatewayException(GatewayErrorCode.SIGN_INVALID, "模拟验签失败");
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
                Map<String, String> rawMap = new ConcurrentHashMap<String, String>();
                for (Map.Entry<String, Object> entry : body.entrySet()) {
                    rawMap.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                event.setRaw(rawMap);
                return event;
            }

            @Override
            public String successReply() {
                return "SUCCESS";
            }

            @Override
            public String failureReply() {
                return "FAIL";
            }
        };
    }

    /** 渠道自定义参数。 */
    public static class TestParams extends ChannelConfig {
        public String channelAppId() {
            return APP_KEY;
        }
    }

    /** 测试用配置提供者：单一应用、单一渠道。 */
    public static class TestConfigProvider implements ChannelConfigProvider {

        private final String configId;

        public TestConfigProvider(String configId) {
            this.configId = configId;
        }

        @Override
        public ChannelConfig getConfig(String configId, String channelCode) {
            if (!CODE.equals(channelCode)) {
                throw new GatewayException(GatewayErrorCode.CONFIG_MISSING, "渠道配置缺失: {}", channelCode);
            }
            return new TestParams()
                    .setConfigId(configId)
                    .setChannelCode(channelCode);
        }
    }
}
