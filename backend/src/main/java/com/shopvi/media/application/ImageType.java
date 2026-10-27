package com.shopvi.media.application;

import java.util.Arrays;
import java.util.Optional;

/**
 * Định dạng ảnh được phép, nhận diện bằng magic bytes thay vì tin Content-Type/đuôi file client gửi.
 */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    /** Số byte đầu cần đọc để nhận diện. */
    public static final int HEADER_SIZE = 12;

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] header) {
        return Arrays.stream(values()).filter(t -> t.matches(header)).findFirst();
    }

    private boolean matches(byte[] h) {
        return switch (this) {
            case JPEG -> startsWith(h, 0, 0xFF, 0xD8, 0xFF);
            case PNG -> startsWith(h, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            // "RIFF" ???? "WEBP"
            case WEBP -> startsWith(h, 0, 'R', 'I', 'F', 'F') && startsWith(h, 8, 'W', 'E', 'B', 'P');
        };
    }

    private static boolean startsWith(byte[] h, int offset, int... expected) {
        if (h.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((h[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
