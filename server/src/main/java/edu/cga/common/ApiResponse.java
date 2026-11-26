package edu.cga.common;

/**
 * 统一返回体 { code, data, message }，与前端 mock 的返回格式一致。
 */
public record ApiResponse<T>(int code, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, data, "操作成功");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(200, data, message);
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, data, message);
    }
}
