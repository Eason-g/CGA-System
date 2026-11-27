package edu.cga.common;

import lombok.Getter;

/**
 * 业务异常。code 同时作为 HTTP 状态码；data 可携带校验结果（如规则引擎的 checks），前端会展示。
 * <p>
 * messageEn 为可选的英文提示；请求头 Accept-Language 为英文时由 {@link GlobalExceptionHandler} 作为 messageEn 输出。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final transient Object data;
    private final String messageEn;

    public BizException(int code, String message) {
        this(code, message, null, null);
    }

    public BizException(int code, String message, Object data) {
        this(code, message, null, data);
    }

    public BizException(int code, String message, String messageEn, Object data) {
        super(message);
        this.code = code;
        this.messageEn = messageEn;
        this.data = data;
    }

    public BizException(int code, BiText text, Object data) {
        this(code, text.zh(), text.en(), data);
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    public static BizException badRequest(String message, String messageEn) {
        return new BizException(400, message, messageEn, null);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    public static BizException forbidden(String message, String messageEn) {
        return new BizException(403, message, messageEn, null);
    }

    public static BizException forbidden(BiText text) {
        return new BizException(403, text, null);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }
}
