package com.sfi.conflux.common.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Date;
import java.util.Locale;

/**
 * 日期工具：全局格式常量与两套时间域（java.time / {@link Date}）的常用操作。
 *
 * <p>方法按时间域分两区组织：</p>
 * <ul>
 *   <li><b>java.time 域</b>——主航道，新代码统一用这一区；</li>
 *   <li><b>Date 域</b>——存量桥接，服务老项目/老接口的 {@code java.util.Date} 字段，
 *       全部内部复用 java.time 实现，新代码优先 java.time 域。</li>
 * </ul>
 *
 * <p>格式常量的唯一出口——web 层 Jackson 序列化、业务层入库/展示统一引用，
 * 避免「web 一个格式、日志一个格式」的口径漂移。线程安全：{@link DateTimeFormatter} 不可变。</p>
 *
 * @author lianghang
 */
public final class DateUtils {

    // ==================================================================
    // 格式常量与 formatter（唯一出口）
    // ==================================================================

    /** 全局日期时间格式（web 序列化 / DB 展示统一口径） */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 全局日期格式 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 紧凑格式（流水号/文件名/日志前缀），例：20261030234159 */
    public static final String COMPACT_PATTERN = "yyyyMMddHHmmss";

    /** 日期段紧凑格式（单号/分区键），例：20261031 */
    public static final String DATE_COMPACT_PATTERN = "yyyyMMdd";

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    public static final DateTimeFormatter COMPACT_FORMATTER = DateTimeFormatter.ofPattern(COMPACT_PATTERN);

    public static final DateTimeFormatter DATE_COMPACT_FORMATTER = DateTimeFormatter.ofPattern(DATE_COMPACT_PATTERN);

    // ==================================================================
    // java.time 域（主航道：新代码统一用这区）
    // ==================================================================

    /** 当前时间格式化为全局日期时间串 */
    public static String now() {
        return format(LocalDateTime.now());
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FORMATTER);
    }

    /** 日期段紧凑串（yyyyMMdd），单号生成/分区键统一口径 */
    public static String formatDateCompact(LocalDate date) {
        return date == null ? null : date.format(DATE_COMPACT_FORMATTER);
    }

    /** 解析全局格式日期时间串；null/空串返回 null，格式非法抛异常（ fail-fast） */
    public static LocalDateTime parseDateTime(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return LocalDateTime.parse(text.trim(), DATE_TIME_FORMATTER);
    }

    /** 解析全局格式日期串；null/空串返回 null */
    public static LocalDate parseDate(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return LocalDate.parse(text.trim(), DATE_FORMATTER);
    }

    /** LocalDateTime → 系统默认时区毫秒时间戳 */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 毫秒时间戳 → LocalDateTime（系统默认时区） */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    /** 格式化为紧凑串 {@code yyyyMMddHHmmss}；null 返回 null。 */
    public static String formatCompact(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(COMPACT_FORMATTER);
    }

    /** 格式化为中文星期，例：星期一 … 星期日；null 返回 null。 */
    public static String chineseWeek(LocalDate date) {
        return date == null ? null : date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);
    }

    /** 距当天结束（次日零点）的剩余秒数——「当日有效」类过期的标准算法。 */
    public static long dayBalanceSeconds() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.plusDays(1).with(LocalTime.MIN)).get(ChronoUnit.SECONDS);
    }

    // ==================================================================
    // Date 域（存量桥接：老项目/老接口的 java.util.Date 字段用；新代码优先 java.time 域）
    // ==================================================================

    /** {@link Date} → LocalDateTime（系统默认时区）；null 原样返回。 */
    public static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /** LocalDateTime → {@link Date}（系统默认时区）；null 原样返回。 */
    public static Date toDate(LocalDateTime dateTime) {
        return dateTime == null ? null : Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** {@link Date} → 系统默认时区毫秒时间戳 */
    public static long toEpochMilli(Date date) {
        return toEpochMilli(toLocalDateTime(date));
    }

    public static String format(Date date) {
        return format(toLocalDateTime(date));
    }

    public static String formatDate(Date date) {
        LocalDateTime dateTime = toLocalDateTime(date);
        return dateTime == null ? null : formatDate(dateTime.toLocalDate());
    }

    public static String formatCompact(Date date) {
        return formatCompact(toLocalDateTime(date));
    }

    public static String formatDateCompact(Date date) {
        LocalDateTime dateTime = toLocalDateTime(date);
        return dateTime == null ? null : formatDateCompact(dateTime.toLocalDate());
    }

    /**
     * 加减天数（日历语义：按日历字段加，跨夏令时不偏移；负数即减）；
     * 等价 commons-lang {@code addDays} / hutool {@code offsetDay}；null 返回 null。
     */
    public static Date offsetDay(Date date, int days) {
        return offset(date, ChronoUnit.DAYS, days);
    }

    /** 加减小时（日历语义）；负数即减；null 返回 null。 */
    public static Date offsetHour(Date date, int hours) {
        return offset(date, ChronoUnit.HOURS, hours);
    }

    /** 加减分钟（日历语义）；负数即减；null 返回 null。 */
    public static Date offsetMinute(Date date, int minutes) {
        return offset(date, ChronoUnit.MINUTES, minutes);
    }

    /** 当天零点 {@code 00:00:00.000}；null 返回 null。查询范围下界常用。 */
    public static Date beginOfDay(Date date) {
        if (date == null) {
            return null;
        }
        return Date.from(toLocalDateTime(date).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** 当天末尾 {@code 23:59:59.999}；null 返回 null。查询范围上界常用。 */
    public static Date endOfDay(Date date) {
        if (date == null) {
            return null;
        }
        return Date.from(toLocalDateTime(date).toLocalDate().atTime(LocalTime.MAX)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    /** 加减统一实现：走 ZonedDateTime 按日历字段加（非「+24h」毫秒法，跨夏令时不偏移）。 */
    private static Date offset(Date date, TemporalUnit unit, int amount) {
        if (date == null) {
            return null;
        }
        return Date.from(toLocalDateTime(date).atZone(ZoneId.systemDefault()).plus(amount, unit).toInstant());
    }

    private DateUtils() {
    }
}
