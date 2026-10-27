package com.shopvi.media.application;

import java.io.InputStream;

/**
 * Cổng lưu trữ object (MinIO ở dev, S3/R2 ở production).
 */
public interface ObjectStorage {

    /**
     * Lưu object và trả về URL public của nó.
     */
    String put(String key, InputStream content, long size, String contentType);
}
