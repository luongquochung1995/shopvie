package com.shopvi.common.exception;

/**
 * Chi tiết một lỗi, thường là lỗi validate của một field.
 */
public record ErrorDetail(String field, String message) {
}
