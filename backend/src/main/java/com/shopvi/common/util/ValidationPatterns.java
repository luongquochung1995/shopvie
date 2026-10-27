package com.shopvi.common.util;

/** Regex dùng chung cho {@code @Pattern} ở các request DTO. */
public final class ValidationPatterns {

    /** URL ảnh tuyệt đối http(s), không có khoảng trắng (chặn {@code javascript:}, {@code data:}...). */
    public static final String HTTP_URL = "^https?://\\S+$";
    public static final String HTTP_URL_MESSAGE = "must be an absolute http(s) URL";

    private ValidationPatterns() {
    }
}
