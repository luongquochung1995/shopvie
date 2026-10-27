package com.shopvi.common.exception;

import java.util.List;
import lombok.Getter;

/**
 * Lỗi nghiệp vụ có mã lỗi xác định; được {@code GlobalExceptionHandler} chuyển thành response lỗi chuẩn.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<ErrorDetail> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<ErrorDetail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }
}
