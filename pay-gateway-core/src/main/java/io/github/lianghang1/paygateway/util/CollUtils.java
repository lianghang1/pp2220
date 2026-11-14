package io.github.lianghang1.paygateway.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 集合工具统一入口：组合门面——委托 guava {@code Sets/Lists/Immutable*}，
 * 只收高频方法；guava 的高级结构（BiMap/Multimap/Table/Range）直接
 * import guava 原生类使用，门面不转发。
 *
 * @author lianghang
 */
public final class CollUtils {

    private CollUtils() {
    }

    /** null 或空集合 → true */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /** 非 null 且非空 → true */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /** null 或空 Map → true */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /** 非 null 且非空 Map → true */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /** null 安全：null 返回空集合，否则原样返回 */
    public static <T> Collection<T> emptyIfNull(Collection<T> collection) {
        return collection == null ? java.util.Collections.emptyList() : collection;
    }

    /** 交集：a 和 b 都有的元素 */
    public static <E> Set<E> intersection(Set<E> a, Set<E> b) {
        return Sets.intersection(a, b);
    }

    /** 并集：a 或 b 的全部元素 */
    public static <E> Set<E> union(Set<E> a, Set<E> b) {
        return Sets.union(a, b);
    }

    /** 差集：在 a 中但不在 b 中 */
    public static <E> Set<E> difference(Set<E> a, Set<E> b) {
        return Sets.difference(a, b);
    }

    /** 大列表按 size 分批（批量入库/分页调用标配），size 必须 &gt; 0 */
    public static <T> List<List<T>> partition(List<T> list, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("CollUtils: size 必须 > 0, 当前=" + size);
        }
        return Lists.partition(list, size);
    }

    /** 不可变 List：null/空 → 空不可变集合；防御常量集合被误改 */
    public static <T> List<T> immutableList(Collection<T> collection) {
        return collection == null || collection.isEmpty()
                ? ImmutableList.of() : ImmutableList.copyOf(collection);
    }

    /** 不可变 Set：null/空 → 空不可变集合 */
    public static <T> Set<T> immutableSet(Collection<T> collection) {
        return collection == null || collection.isEmpty()
                ? ImmutableSet.of() : ImmutableSet.copyOf(collection);
    }

    /** 不可变 Map：null/空 → 空不可变集合 */
    public static <K, V> Map<K, V> immutableMap(Map<K, V> map) {
        return map == null || map.isEmpty()
                ? ImmutableMap.of() : ImmutableMap.copyOf(map);
    }
}
