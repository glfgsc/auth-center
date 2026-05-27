package com.auth.center.common;

/**
 * 统一 API 响应包装器.
 *
 * <p>所有 Controller 层接口统一返回此对象，前端根据 {@code code} 判断请求是否成功。</p>
 *
 * @param <T> 响应数据类型
 */
public class Result<T> {

    /** 成功状态码 — 与 BI / Flow Engine 统一使用 200 */
    private static final int SUCCESS_CODE = 200;

    /** 默认失败状态码 */
    private static final int FAIL_CODE = 500;

    /** 状态码，200 表示成功，非 200 表示失败 */
    private int code;

    /** 提示消息 */
    private String message;

    /** 响应数据 */
    private T data;

    /**
     * 无参构造——序列化框架需要.
     */
    public Result() {
    }

    /**
     * 全参构造.
     *
     * @param code    状态码
     * @param message 提示消息
     * @param data    响应数据
     */
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /* ---------- 静态工厂方法 ---------- */

    /**
     * 成功响应（携带数据）.
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功的 Result 实例
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "success", data);
    }

    /**
     * 成功响应（不携带数据）.
     *
     * @param <T> 数据类型
     * @return 成功的 Result 实例
     */
    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS_CODE, "success", null);
    }

    /**
     * 失败响应（仅消息）.
     *
     * @param message 错误提示
     * @param <T>     数据类型
     * @return 失败的 Result 实例
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(FAIL_CODE, message, null);
    }

    /**
     * 失败响应（自定义状态码 + 消息）.
     *
     * @param code    自定义错误码
     * @param message 错误提示
     * @param <T>     数据类型
     * @return 失败的 Result 实例
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /* ---------- Getters / Setters ---------- */

    /**
     * 获取状态码.
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置状态码.
     *
     * @param code 状态码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取提示消息.
     *
     * @return 提示消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置提示消息.
     *
     * @param message 提示消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取响应数据.
     *
     * @return 响应数据
     */
    public T getData() {
        return data;
    }

    /**
     * 设置响应数据.
     *
     * @param data 响应数据
     */
    public void setData(T data) {
        this.data = data;
    }
}
