package io.github.lianghang1.conflux.invoice.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 发票域单号生成器（不修改已发布的 common 1.0.0，各能力域自带前缀规则）。
 *
 * <p>前缀 + 17 位时间戳 + 3 位循环序列，单机线程安全。这些内部标识仅用于渠道交互
 * 载荷（如授权会话号、文件号），业务单号由使用方提供、查重也由使用方完成。</p>
 *
 * @author lianghang
 */
public final class InvoiceIds {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong SEQ = new AtomicLong();

    private InvoiceIds() {
    }

    /** 发票单据号（INV）。 */
    public static String invoice() {
        return next("INV");
    }

    /** 红字确认单号（RA）。 */
    public static String redApply() {
        return next("RA");
    }

    /** 授权会话号（AUTH）。 */
    public static String authSession() {
        return next("AUTH");
    }

    /** 版式文件号（FILE）。 */
    public static String file() {
        return next("FILE");
    }

    private static String next(String prefix) {
        return prefix + LocalDateTime.now().format(TS)
                + String.format("%03d", (int) (SEQ.incrementAndGet() % 1000));
    }
}
