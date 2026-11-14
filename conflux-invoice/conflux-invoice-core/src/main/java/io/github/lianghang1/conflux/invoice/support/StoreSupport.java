package io.github.lianghang1.conflux.invoice.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * 内存 Store 参考实现的内部工具。
 *
 * <p><b>深拷贝是 CAS 契约的前提</b>：如果 Store 直接保存调用方传入的对象引用，
 * 调用方后续对该对象的字段修改会立刻反映到「已持久化」的数据上，
 * {@code tryUpdate(invoice, expectedVersion)} 的版本比较就形同虚设。
 * 因此所有内存实现都只在读写边界保存/返回副本。</p>
 *
 * @author lianghang
 */
public final class StoreSupport {

    private StoreSupport() {
    }

    /** 基于 JDK 序列化的深拷贝（实体均实现 Serializable）。 */
    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T copy(T value) {
        if (value == null) {
            return null;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(value);
            out.flush();
            ObjectInputStream in = new ObjectInputStream(
                    new ByteArrayInputStream(bos.toByteArray()));
            return (T) in.readObject();
        } catch (Exception e) {
            throw new IllegalStateException("内存 Store 深拷贝失败", e);
        }
    }

    /** 组合索引键。 */
    public static String key(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append("::");
            }
            sb.append(parts[i] == null ? "" : parts[i]);
        }
        return sb.toString();
    }
}
