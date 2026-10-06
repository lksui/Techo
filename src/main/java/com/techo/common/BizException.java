package com.techo.common;

/**
 * 业务异常。code 与文档 §6.3 的错误码对应：
 * 400 参数校验失败，404 条目不存在。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }
}
