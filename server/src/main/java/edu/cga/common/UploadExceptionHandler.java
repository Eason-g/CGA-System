package edu.cga.common;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 上传相关异常（P1 第三批）：超出 spring.servlet.multipart 大小上限时返回 { code: 400, message } JSON，
 * 而不是容器的错误页。单独成类并优先于 GlobalExceptionHandler 生效，避免改动共享文件。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, "文件大小超过 10MB 上限", null));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, "请选择要上传的文件", null));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMultipart(MultipartException e) {
        // 超限有时被包装成普通 MultipartException（原因链里是容器的大小异常）
        Throwable t = e;
        while (t != null) {
            String n = t.getClass().getSimpleName();
            if (n.contains("SizeLimitExceeded") || n.contains("SizeException") || n.contains("MaxUploadSize")) {
                return handleTooLarge(new MaxUploadSizeExceededException(-1, e));
            }
            t = t.getCause();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(400, "上传请求格式错误，请使用 multipart/form-data", null));
    }
}
