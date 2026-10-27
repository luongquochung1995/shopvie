package com.shopvi.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@IntegrationTest
class MeIT {

    @LocalServerPort
    private int port;

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        token = api.registerRandom();
    }

    @Test
    void meRequiresAuthentication() {
        var response = api.get("/me", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().at("/error/code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void updateProfile() {
        // SĐT ngẫu nhiên: DB integration test có thể được dùng lại giữa các lần chạy (SĐT là UNIQUE)
        String phone = "+8491" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
        var response = api.put("/me", token, Map.of("fullName", "Trần Thị B", "phone", phone));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().at("/data/fullName").asText()).isEqualTo("Trần Thị B");
        assertThat(response.getBody().at("/data/phone").asText()).isEqualTo(phone);
    }

    @Test
    void addressBookKeepsExactlyOneDefault() {
        long first = id(api.post("/me/addresses", token, address("1", false)).getBody());
        long second = id(api.post("/me/addresses", token, address("2", false)).getBody());
        long third = id(api.post("/me/addresses", token, address("3", true)).getBody());

        JsonNode list = api.get("/me/addresses", token).getBody().at("/data");
        assertThat(list).hasSize(3);
        assertThat(list.get(0).at("/id").asLong()).isEqualTo(third);
        // Địa giới 2 cấp: không có mã huyện
        assertThat(list.get(0).at("/districtCode").isNull()).isTrue();
        assertThat(list.get(0).at("/wardCode").asText()).isEqualTo("26740");
        assertThat(list.findValues("isDefault").stream().filter(JsonNode::asBoolean)).hasSize(1);

        assertThat(api.patch("/me/addresses/" + first + "/default", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/me/addresses", token).getBody().at("/data/0/id").asLong()).isEqualTo(first);

        // Xóa địa chỉ mặc định → địa chỉ mới nhất còn lại thành mặc định
        assertThat(api.delete("/me/addresses/" + first, token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode after = api.get("/me/addresses", token).getBody().at("/data");
        assertThat(after).hasSize(2);
        assertThat(after.get(0).at("/id").asLong()).isEqualTo(third);
        assertThat(after.get(0).at("/isDefault").asBoolean()).isTrue();
        assertThat(after.get(1).at("/id").asLong()).isEqualTo(second);
    }

    @Test
    void cannotAccessAddressOfAnotherUser() {
        long mine = id(api.post("/me/addresses", token, address("mine", false)).getBody());
        String stranger = api.registerRandom();

        assertThat(api.put("/me/addresses/" + mine, stranger, address("x", false)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.delete("/me/addresses/" + mine, stranger).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.get("/me/addresses", stranger).getBody().at("/data")).isEmpty();
    }

    @Test
    void invalidAddressIsRejected() {
        Map<String, Object> body = new HashMap<>(address("1", false));
        body.put("phone", "12345");
        body.put("detail", "");

        var response = api.post("/me/addresses", token, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().at("/error/details").findValuesAsText("field"))
                .containsExactlyInAnyOrder("phone", "detail");
    }

    private static Map<String, Object> address(String n, boolean isDefault) {
        return Map.of("receiverName", "Người nhận " + n, "phone", "0901234567", "provinceCode", "79",
                "wardCode", "26740", "detail", n + " Lê Lợi", "isDefault", isDefault);
    }

    private static long id(JsonNode body) {
        return body.at("/data/id").asLong();
    }
}
