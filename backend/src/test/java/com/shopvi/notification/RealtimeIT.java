package com.shopvi.notification;

import static com.shopvi.support.StompTestClient.next;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import com.shopvi.support.StompTestClient;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P6-1 → P6-3 (DoD Phase 6): WebSocket STOMP over SockJS xác thực bằng JWT; seller đổi trạng thái đơn → người mua nhận
 * thông báo realtime; chat 2 chiều có "đã xem".
 */
@IntegrationTest
class RealtimeIT {

    private static final String SELLER = "seller1@shopvi.local";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;
    private long variant;
    private long shopId;
    private int originalStock;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT v.id, p.shop_id FROM product_variants v JOIN products p ON p.id = v.product_id
                JOIN shops s ON s.id = p.shop_id JOIN users u ON u.id = s.owner_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND s.status = 'ACTIVE' AND u.email = ?
                ORDER BY v.id LIMIT 1""", SELLER);
        variant = ((Number) row.get("id")).longValue();
        shopId = ((Number) row.get("shop_id")).longValue();
        originalStock = jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variant);
        jdbc.update("UPDATE product_variants SET stock = 50 WHERE id = ?", variant);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE product_variants SET stock = ? WHERE id = ?", originalStock, variant);
    }

    @Test
    void connectRequiresValidToken() {
        assertThatThrownBy(() -> StompTestClient.connect(port, "not-a-jwt").close())
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void sellerChangingOrderStatusNotifiesBuyerInRealtime() throws Exception {
        String buyer = api.registerRandom();
        String seller = api.login(SELLER);
        try (StompTestClient buyerWs = StompTestClient.connect(port, buyer);
             StompTestClient sellerWs = StompTestClient.connect(port, seller)) {
            BlockingQueue<JsonNode> buyerInbox = buyerWs.subscribe("/user/queue/notifications");
            BlockingQueue<JsonNode> sellerInbox = sellerWs.subscribe("/user/queue/notifications");

            String code = placeCodOrder(buyer);
            JsonNode newOrder = waitFor(sellerInbox, "NEW_ORDER", code);
            assertThat(newOrder.at("/notification/link").asText()).isEqualTo("/seller-center/orders/" + code);

            assertThat(api.post("/seller/orders/" + code + "/confirm", seller, Map.of()).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            JsonNode push = waitFor(buyerInbox, "ORDER_STATUS", code);
            assertThat(push.at("/notification/params/status").asText()).isEqualTo("CONFIRMED");
            assertThat(push.at("/notification/link").asText()).isEqualTo("/account/orders/" + code);
            assertThat(push.at("/unreadCount").asLong()).isPositive();

            // Người mua tự hủy: báo cho seller, không báo lại người mua
            api.post("/orders/" + code + "/cancel", buyer, Map.of("reason", "Đổi ý"));
            waitFor(sellerInbox, "ORDER_CANCELLED_BY_BUYER", code);
        }

        JsonNode list = api.get("/notifications", buyer).getBody().at("/data");
        assertThat(list.at("/items/0/type").asText()).isEqualTo("ORDER_STATUS");
        assertThat(list.at("/unreadCount").asLong()).isEqualTo(1);
        long id = list.at("/items/0/id").asLong();
        assertThat(api.post("/notifications/" + id + "/read", buyer, null).getBody().at("/data/read").asBoolean()).isTrue();
        assertThat(api.get("/notifications/unread-count", buyer).getBody().at("/data/count").asLong()).isZero();
        assertThat(api.post("/notifications/" + id + "/read", api.registerRandom(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void buyerAndSellerChatBothWaysWithReadReceipts() throws Exception {
        String buyer = api.registerRandom();
        String seller = api.login(SELLER);
        JsonNode conversation = api.post("/chat/conversations", buyer, Map.of("shopId", shopId)).getBody().at("/data");
        long conversationId = conversation.at("/id").asLong();
        assertThat(conversation.at("/side").asText()).isEqualTo("BUYER");
        // Mở lại → cùng hội thoại
        assertThat(api.post("/chat/conversations", buyer, Map.of("shopId", shopId)).getBody().at("/data/id").asLong())
                .isEqualTo(conversationId);
        assertThat(api.post("/chat/conversations", seller, Map.of("shopId", shopId)).getBody().at("/error/code").asText())
                .isEqualTo("CHAT_WITH_OWN_SHOP");

        try (StompTestClient buyerWs = StompTestClient.connect(port, buyer);
             StompTestClient sellerWs = StompTestClient.connect(port, seller)) {
            BlockingQueue<JsonNode> buyerChat = buyerWs.subscribe("/user/queue/chat");
            BlockingQueue<JsonNode> sellerChat = sellerWs.subscribe("/user/queue/chat");

            String path = "/chat/conversations/" + conversationId + "/messages";
            assertThat(api.post(path, buyer, Map.of("content", "Shop ơi, còn size M không?")).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
            JsonNode atSeller = messageIn(sellerChat, conversationId);
            assertThat(atSeller.at("/message/content").asText()).isEqualTo("Shop ơi, còn size M không?");
            assertThat(atSeller.at("/message/senderSide").asText()).isEqualTo("BUYER");
            assertThat(atSeller.at("/unread").asInt()).isEqualTo(1);
            messageIn(buyerChat, conversationId); // người gửi cũng nhận (đồng bộ nhiều tab)

            JsonNode sellerView = findConversation(api.get("/chat/conversations", seller).getBody().at("/data/items"),
                    conversationId);
            assertThat(sellerView.at("/side").asText()).isEqualTo("SHOP");
            assertThat(sellerView.at("/unread").asInt()).isEqualTo(1);
            assertThat(api.get("/chat/unread-count", seller).getBody().at("/data/count").asLong()).isPositive();

            JsonNode reply = api.post(path, seller, Map.of("content", "Còn ạ!")).getBody().at("/data");
            JsonNode atBuyer = messageIn(buyerChat, conversationId);
            assertThat(atBuyer.at("/message/content").asText()).isEqualTo("Còn ạ!");
            assertThat(atBuyer.at("/message/senderSide").asText()).isEqualTo("SHOP");
            messageIn(sellerChat, conversationId);

            // Người mua xem → seller nhận "đã xem" tới tin trả lời
            api.post("/chat/conversations/" + conversationId + "/read", buyer, null);
            JsonNode read = next(sellerChat, 10);
            assertThat(read.at("/type").asText()).isEqualTo("READ");
            assertThat(read.at("/lastReadMessageId").asLong()).isEqualTo(reply.at("/id").asLong());
        }

        JsonNode page = api.get("/chat/conversations/" + conversationId + "/messages", seller).getBody().at("/data");
        assertThat(page.at("/items")).hasSize(2);
        assertThat(page.at("/items/0/content").asText()).isEqualTo("Shop ơi, còn size M không?");
        assertThat(page.at("/otherLastReadMessageId").asLong()).isEqualTo(page.at("/items/1/id").asLong());
        assertThat(api.get("/chat/conversations/" + conversationId + "/messages", api.registerRandom()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.post("/chat/conversations/" + conversationId + "/messages", buyer, Map.of("content", " "))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ----- helpers

    private String placeCodOrder(String buyer) {
        long addressId = api.post("/me/addresses", buyer, Map.of("receiverName", "Người nhận", "phone", "0901234567",
                "provinceCode", "79", "wardCode", "26740", "detail", "1 Lê Lợi", "isDefault", true))
                .getBody().at("/data/id").asLong();
        api.post("/cart/items", buyer, Map.of("variantId", variant, "quantity", 1));
        return api.post("/checkout", buyer, Map.of("addressId", addressId, "paymentMethod", "COD"))
                .getBody().at("/data/orders/0/code").asText();
    }

    /** Bỏ qua tin của sự kiện khác (seller có thể nhận đơn mới từ test khác) tới khi gặp đúng loại + mã đơn. */
    private static JsonNode waitFor(BlockingQueue<JsonNode> inbox, String type, String orderCode) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode push = next(inbox, 15);
            if (type.equals(push.at("/notification/type").asText())
                    && orderCode.equals(push.at("/notification/params/orderCode").asText())) {
                return push;
            }
        }
        throw new AssertionError("No " + type + " notification for " + orderCode);
    }

    private static JsonNode messageIn(BlockingQueue<JsonNode> inbox, long conversationId) throws InterruptedException {
        JsonNode push = next(inbox, 10);
        assertThat(push.at("/type").asText()).isEqualTo("MESSAGE");
        assertThat(push.at("/conversationId").asLong()).isEqualTo(conversationId);
        return push;
    }

    private static JsonNode findConversation(JsonNode items, long id) {
        for (JsonNode c : items) {
            if (c.at("/id").asLong() == id) {
                return c;
            }
        }
        throw new AssertionError("conversation " + id + " not listed");
    }
}
