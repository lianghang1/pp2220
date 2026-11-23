package com.sfi.conflux.common.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 集合工具统一入口：只收高频方法。
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
        return collection == null ? Collections.emptyList() : collection;
    }

    /** 交集：a 和 b 都有的元素（返回独立新 Set，不影响源集合） */
    public static <E> Set<E> intersection(Set<E> a, Set<E> b) {
        Set<E> result = new HashSet<E>();
        if (a != null && b != null) {
            for (E e : a) {
                if (b.contains(e)) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    /** 并集：a 或 b 的全部元素（返回独立新 Set） */
    public static <E> Set<E> union(Set<E> a, Set<E> b) {
        Set<E> result = new HashSet<E>();
        if (a != null) {
            result.addAll(a);
        }
        if (b != null) {
            result.addAll(b);
        }
        return result;
    }

    /** 差集：在 a 中但不在 b 中（返回独立新 Set） */
    public static <E> Set<E> difference(Set<E> a, Set<E> b) {
        Set<E> result = new HashSet<E>();
        if (a != null) {
            result.addAll(a);
            if (b != null) {
                result.removeAll(b);
            }
        }
        return result;
    }

    /** 大列表按 size 分批（批量入库/分页调用标配），size 必须 &gt; 0；返回只读视图。 */
    public static <T> List<List<T>> partition(final List<T> list, final int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("CollUtils: size 必须 > 0, 当前=" + size);
        }
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        final int full = list.size() / size;
        final boolean hasTail = list.size() % size != 0;
        final int chunkCount = full + (hasTail ? 1 : 0);
        return new AbstractList<List<T>>() {
            @Override
            public List<T> get(int index) {
                if (index < 0 || index >= chunkCount) {
                    throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + chunkCount);
                }
                int from = index * size;
                int to = Math.min(from + size, list.size());
                return Collections.unmodifiableList(list.subList(from, to));
            }

            @Override
            public int size() {
                return chunkCount;
            }
        };
    }

    /** 不可变 List：null/空 → 空不可变集合；防御常量集合被误改 */
    public static <T> List<T> immutableList(Collection<T> collection) {
        return collection == null || collection.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<T>(collection));
    }

    /** 不可变 Set：null/空 → 空不可变集合 */
    public static <T> Set<T> immutableSet(Collection<T> collection) {
        return collection == null || collection.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<T>(collection));
    }

    /** 不可变 Map：null/空 → 空不可变集合 */
    public static <K, V> Map<K, V> immutableMap(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<K, V> copy = new java.util.LinkedHashMap<K, V>(map);
        return Collections.unmodifiableMap(copy);
    }
}
