package com.shopvi.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;

/** Cần MinIO từ Testcontainers nên không chạy ở chế độ hạ tầng ngoài. */
@IntegrationTest
@DisabledIfSystemProperty(named = "it.external", matches = "true")
class MediaUploadIT {

    /** PNG 1x1 hợp lệ. */
    private static final byte[] PNG = HexFormat.of().parseHex(
            "89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C4890000000D4944415478DA63FCFFFF3F"
                    + "0300050001FF5CCD3F0000000049454E44AE426082");

    @LocalServerPort
    private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    void uploadedImageIsPubliclyReadable() throws Exception {
        String token = api.registerRandom();

        var response = api.post("/media/upload", token, multipart(PNG, "avatar.png"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String url = response.getBody().at("/data/url").asText();
        assertThat(url).matches(".*/shopvi-media/images/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        assertThat(response.getBody().at("/data/contentType").asText()).isEqualTo("image/png");

        HttpResponse<byte[]> download = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(PNG);
    }

    @Test
    void rejectsNonImageEvenWithImageExtension() {
        var response = api.post("/media/upload", api.registerRandom(), multipart("%PDF-1.7".getBytes(), "fake.jpg"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void requiresAuthentication() {
        assertThat(api.post("/media/upload", null, multipart(PNG, "a.png")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static LinkedMultiValueMap<String, Object> multipart(byte[] content, String filename) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return body;
    }
}
