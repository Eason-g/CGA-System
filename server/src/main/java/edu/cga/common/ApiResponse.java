package edu.cga.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一返回体 { code, data, message }，与前端 mock 的返回格式一致。
 * <p>
 * messageEn 为可选的英文提示（docs/双语设计.md 第 3 节）：只在请求头 Accept-Language 为英文且有英文文案时输出，
 * 为空时不出现在 JSON 中；message 始终是中文。
 */
public record ApiResponse<T>(int code, T data, String message,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String messageEn) {

    public ApiResponse(int code, T data, String message) {
        this(code, data, message, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, data, "操作成功");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(200, data, message);
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, data, message);
    }

    public static <T> ApiResponse<T> fail(int code, String message, String messageEn, T data) {
        return new ApiResponse<>(code, data, message, messageEn);
    }
}
