package com.shopvi.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Giới hạn tần suất gọi endpoint (token bucket trên Redis, dùng chung giữa các instance). Hạn mức lấy từ
 * {@code app.rate-limit.rules.<value>}; vượt hạn mức → 429 {@code TOO_MANY_REQUESTS} kèm header {@code Retry-After}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Tên quy tắc trong cấu hình (vd. login, checkout). */
    String value();

    /** Đếm theo IP (endpoint chưa đăng nhập) hay theo user đã đăng nhập. */
    Key key() default Key.IP;

    enum Key { IP, USER }
}
