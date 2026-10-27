package com.shopvi.common.web;

import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import java.util.List;

/**
 * Response lỗi chuẩn: {@code { "success": false, "error": { "code", "message", "details" } }}.
 */
public record ApiErrorResponse(boolean success, ApiError error) {

    public record ApiError(String code, String message, List<ErrorDetail> details) {
    }

    public static ApiErrorResponse of(ErrorCode code, String message, List<ErrorDetail> details) {
        return new ApiErrorResponse(false, new ApiError(code.name(), message, List.copyOf(details)));
    }

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return of(code, message, List.of());
    }

    public static ApiErrorResponse of(ErrorCode code) {
        return of(code, code.getDefaultMessage());
    }
}
