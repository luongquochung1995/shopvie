package com.shopvi.media.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình lưu trữ media trên MinIO (dev) hoặc dịch vụ tương thích S3 như AWS S3 / Cloudflare R2 (production) —
 * prefix {@code app.media}.
 *
 * @param endpoint            endpoint S3 API, vd. http://localhost:9000, https://<account>.r2.cloudflarestorage.com
 * @param publicBaseUrl       gốc URL trả cho client (CDN ở production), vd. http://localhost:9000, https://cdn.example.com
 * @param bucket              bucket chứa ảnh (đọc public)
 * @param maxFileSize         dung lượng tối đa mỗi file
 * @param region              region S3 (R2: {@code auto}); null = mặc định của client
 * @param manageBucket        tự tạo bucket + policy đọc public khi khởi động (dev/MinIO); production tạo bằng hạ tầng
 * @param urlIncludesBucket   URL công khai có đoạn {@code /<bucket>} (MinIO path-style); false khi CDN/domain riêng
 *                            trỏ thẳng vào bucket
 */
@Validated
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        @NotBlank String endpoint,
        @NotBlank String publicBaseUrl,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        DataSize maxFileSize,
        String region,
        Boolean manageBucket,
        Boolean urlIncludesBucket) {

    public MediaProperties {
        if (maxFileSize == null) {
            maxFileSize = DataSize.ofMegabytes(5);
        }
        if (region != null && region.isBlank()) {
            region = null;
        }
        manageBucket = manageBucket == null || manageBucket;
        urlIncludesBucket = urlIncludesBucket == null || urlIncludesBucket;
    }
}
