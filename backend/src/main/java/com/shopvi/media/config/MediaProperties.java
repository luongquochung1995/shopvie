package com.shopvi.media.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình lưu trữ media trên MinIO/S3 (prefix {@code app.media}).
 *
 * @param endpoint      endpoint S3 API, vd. http://localhost:9000
 * @param publicBaseUrl gốc URL trả cho client (CDN ở production), vd. http://localhost:9000
 * @param bucket        bucket chứa ảnh (đọc public)
 * @param maxFileSize   dung lượng tối đa mỗi file
 */
@Validated
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        @NotBlank String endpoint,
        @NotBlank String publicBaseUrl,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        DataSize maxFileSize) {

    public MediaProperties {
        if (maxFileSize == null) {
            maxFileSize = DataSize.ofMegabytes(5);
        }
    }
}
