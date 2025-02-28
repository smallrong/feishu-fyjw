package em.backend.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果
 */
@Data
public class R<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;

    // 状态码
    private Integer code;
    
    // 返回信息
    private String msg;
    
    // 返回数据
    private T data;
    
    // 时间戳
    private long timestamp = System.currentTimeMillis();

    // 私有构造
    private R() {}

    // 成功静态方法
    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.code = 200;
        r.msg = "操作成功";
        return r;
    }

    // 成功静态方法
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.msg = "操作成功";
        r.data = data;
        return r;
    }

    // 失败静态方法
    public static <T> R<T> fail() {
        R<T> r = new R<>();
        r.code = 500;
        r.msg = "操作失败";
        return r;
    }

    // 失败静态方法
    public static <T> R<T> fail(String msg) {
        R<T> r = new R<>();
        r.code = 500;
        r.msg = msg;
        return r;
    }

    // 失败静态方法
    public static <T> R<T> fail(Integer code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    // 设置数据
    public R<T> data(T data) {
        this.data = data;
        return this;
    }

    // 设置消息
    public R<T> msg(String msg) {
        this.msg = msg;
        return this;
    }

    // 设置状态码
    public R<T> code(Integer code) {
        this.code = code;
        return this;
    }
} 