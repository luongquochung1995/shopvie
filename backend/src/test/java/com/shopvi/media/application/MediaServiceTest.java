package com.shopvi.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.media.config.MediaProperties;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class MediaServiceTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final MediaProperties properties = new MediaProperties("http://minio:9000", "http://cdn", "k", "s",
            "bucket", DataSize.ofKilobytes(1), null, null, null);
    private final MediaService service = new MediaService(storage, properties,
            Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC));

    @ParameterizedTest
    @CsvSource({
            "FFD8FFE000104A4649460001, image/jpeg, jpg",
            "89504E470D0A1A0A0000000D, image/png, png",
            "524946462400000057454250, image/webp, webp"})
    void detectsImageByMagicBytesAndStoresUnderDatedKey(String headerHex, String contentType, String ext) {
        when(storage.put(anyString(), any(), anyLong(), anyString())).thenReturn("http://cdn/x");
        byte[] content = HexFormat.of().parseHex(headerHex);

        var result = service.uploadImage(new MockMultipartFile("file", "whatever.bin", "text/plain", content));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InputStream> stream = ArgumentCaptor.forClass(InputStream.class);
        verify(storage).put(key.capture(), stream.capture(), eq((long) content.length), eq(contentType));
        assertThat(key.getValue()).matches("images/2026/03/[0-9a-f-]{36}\\." + ext);
        assertThat(result.contentType()).isEqualTo(contentType);
        assertThat(result.url()).isEqualTo("http://cdn/x");
    }

    @Test
    void streamPassedToStorageStartsFromFirstByte() throws Exception {
        byte[] content = HexFormat.of().parseHex("89504E470D0A1A0A0000000D49484452");
        ArgumentCaptor<InputStream> stream = ArgumentCaptor.forClass(InputStream.class);
        when(storage.put(anyString(), stream.capture(), anyLong(), anyString())).thenAnswer(inv -> {
            assertThat(((InputStream) inv.getArgument(1)).readAllBytes()).isEqualTo(content);
            return "url";
        });

        service.uploadImage(new MockMultipartFile("file", "a.png", "image/png", content));
    }

    @Test
    void rejectsFileNotMatchingAllowedSignatures() {
        var pdf = new MockMultipartFile("file", "x.jpg", "image/jpeg", "%PDF-1.7 hello".getBytes());

        assertThatThrownBy(() -> service.uploadImage(pdf))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        verify(storage, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void rejectsTooLargeFile() {
        var big = new MockMultipartFile("file", "a.png", "image/png", new byte[2048]);

        assertThatThrownBy(() -> service.uploadImage(big))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void rejectsEmptyFile() {
        var empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.uploadImage(empty)).isInstanceOf(BusinessException.class);
    }
}
