package edu.cga.common;

import lombok.Getter;

/**
 * 业务异常。code 同时作为 HTTP 状态码；data 可携带校验结果（如规则引擎的 checks），前端会展示。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final transient Object data;

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }
}
