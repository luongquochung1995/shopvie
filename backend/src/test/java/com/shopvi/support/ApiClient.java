package com.shopvi.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Client HTTP gọn cho integration test: không ném lỗi với 4xx/5xx, trả về JSON. */
public final class ApiClient {

    public static final String SEED_PASSWORD = "Password@123";

    private final RestClient rest;
    private final String baseUrl;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port + "/api/v1";
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    public ResponseEntity<JsonNode> get(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null, Map.of());
    }

    /** GET với query đã encode sẵn (không qua URI template, tránh encode 2 lần), vd. callback có chữ ký. */
    public ResponseEntity<JsonNode> getRaw(String pathAndQuery) {
        return rest.get().uri(java.net.URI.create(baseUrl + pathAndQuery)).retrieve().toEntity(JsonNode.class);
    }

    public ResponseEntity<JsonNode> post(String path, String token, Object body) {
        return exchange(HttpMethod.POST, path, token, body, Map.of());
    }

    public ResponseEntity<JsonNode> put(String path, String token, Object body) {
        return exchange(HttpMethod.PUT, path, token, body, Map.of());
    }

    public ResponseEntity<JsonNode> patch(String path, String token) {
        return exchange(HttpMethod.PATCH, path, token, null, Map.of());
    }

    public ResponseEntity<JsonNode> delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, token, null, Map.of());
    }

    public ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String token, Object body,
                                             Map<String, String> headers) {
        RestClient.RequestBodySpec spec = rest.method(method).uri(path);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        headers.forEach(spec::header);
        if (body != null) {
            spec.contentType(body instanceof org.springframework.util.MultiValueMap<?, ?>
                    ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_JSON).body(body);
        }
        return spec.retrieve().toEntity(JsonNode.class);
    }

    /** Đăng nhập tài khoản seed, trả access token. */
    public String login(String email) {
        return post("/auth/login", null, Map.of("email", email, "password", SEED_PASSWORD))
                .getBody().at("/data/accessToken").asText();
    }

    /** Đăng ký user mới với email ngẫu nhiên, trả access token. */
    public String registerRandom() {
        String email = "it-" + java.util.UUID.randomUUID() + "@example.com";
        return post("/auth/register", null, Map.of("email", email, "password", "secret123", "fullName", "IT User"))
                .getBody().at("/data/accessToken").asText();
    }
}
