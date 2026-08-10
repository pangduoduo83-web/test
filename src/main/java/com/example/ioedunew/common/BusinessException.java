package com.example.ioedunew.common;

/**
 * 业务异常:携带业务错误码,由全局异常处理器转成统一响应。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
