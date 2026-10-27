package com.shopvi.media.infra;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.media.application.ObjectStorage;
import com.shopvi.media.config.MediaProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class MinioObjectStorage implements ObjectStorage {

    /** Cho phép đọc public (GET object), ghi vẫn cần credentials. */
    private static final String PUBLIC_READ_POLICY = """
            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
            "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""";

    private final MinioClient minio;
    private final MediaProperties properties;

    /** Tạo bucket + policy đọc public nếu chưa có. MinIO chưa chạy thì chỉ cảnh báo, không chặn khởi động. */
    @EventListener(ApplicationReadyEvent.class)
    void ensureBucket() {
        String bucket = properties.bucket();
        try {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created media bucket '{}'", bucket);
            }
            minio.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket)
                    .config(PUBLIC_READ_POLICY.formatted(bucket)).build());
        } catch (Exception ex) {
            log.warn("Media storage not ready (bucket '{}'): {}", bucket, ex.getMessage());
        }
    }

    @Override
    public String put(String key, InputStream content, long size, String contentType) {
        try {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .stream(content, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            log.error("Upload to object storage failed: {}", key, ex);
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        return "%s/%s/%s".formatted(trimTrailingSlash(properties.publicBaseUrl()), properties.bucket(), key);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
