package com.techo.common;

/**
 * 统一响应格式：{@code {code, message, data}}，code 为 0 表示成功。
 *
 * <p>这个是给 JSON 接口用的，不参与页面渲染，所以用 record 没问题。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
