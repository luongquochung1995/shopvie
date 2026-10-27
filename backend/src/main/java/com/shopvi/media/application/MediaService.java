package com.shopvi.media.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.media.config.MediaProperties;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final ObjectStorage storage;
    private final MediaProperties properties;
    private final Clock clock;

    /** Upload ảnh (jpg/png/webp, ≤ max-file-size) vào {@code images/yyyy/MM/<uuid>.<ext>}. */
    public UploadedMedia uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File is required");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            in.mark(ImageType.HEADER_SIZE);
            byte[] header = in.readNBytes(ImageType.HEADER_SIZE);
            in.reset();
            ImageType type = ImageType.detect(header)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
            String key = "images/%s/%s.%s".formatted(KEY_DATE.format(clock.instant()), UUID.randomUUID(),
                    type.extension());
            String url = storage.put(key, in, file.getSize(), type.contentType());
            return new UploadedMedia(url, type.contentType(), file.getSize());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot read uploaded file");
        }
    }

    public record UploadedMedia(String url, String contentType, long size) {
    }
}
