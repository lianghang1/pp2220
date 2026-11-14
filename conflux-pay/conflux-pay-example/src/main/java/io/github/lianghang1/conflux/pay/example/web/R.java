package io.github.lianghang1.conflux.pay.example.web;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 演示宿主的统一响应体（使用方自有约定，与网关框架无关）。
 *
 * @author lianghang
 */
@Data
@Accessors(chain = true)
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0 成功 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 数据 */
    private T data;

    public static <T> R<T> ok(T data) {
        return new R<T>().setCode(0).setMessage("success").setData(data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<T>().setCode(code).setMessage(message);
    }
}
