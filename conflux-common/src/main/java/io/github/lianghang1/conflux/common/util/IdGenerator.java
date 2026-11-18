package io.github.lianghang1.conflux.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关单号生成器：前缀 + 时间戳（17 位）+ 3 位循环序列，单机线程安全。
 *
 * <p>同毫秒内由 {@link AtomicLong} 循环计数保证不重复，仅要求单机唯一。
 * 这些内部标识只用于渠道交互载荷；业务单号由使用方提供、查重由使用方完成。
 * 所有方法线程安全。</p>
 *
 * @author lianghang
 */
public final class IdGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong SEQ = new AtomicLong();

    private IdGenerator() {
    }

    /** 支付单号（前缀 {@code PAY}）。 */
    public static String nextPaymentId() {
        return next("PAY");
    }

    /** 退款单号（前缀 {@code RFD}）。 */
    public static String nextRefundId() {
        return next("RFD");
    }

    /** 转账单号（前缀 {@code TRF}）。 */
    public static String nextTransferId() {
        return next("TRF");
    }

    /** 分账单号（前缀 {@code PSH}）。 */
    public static String nextProfitShareId() {
        return next("PSH");
    }

    /** 通知记录号（前缀 {@code NTF}）。 */
    public static String nextNotifyId() {
        return next("NTF");
    }

    /** 对账差错记录号（前缀 {@code RCD}）。 */
    public static String nextReconId() {
        return next("RCD");
    }

    private static String next(String prefix) {
        return prefix + LocalDateTime.now().format(TS)
                + String.format("%03d", (int) (SEQ.incrementAndGet() % 1000));
    }
}
