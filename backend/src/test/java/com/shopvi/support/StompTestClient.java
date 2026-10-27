package com.shopvi.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/** Client STOMP over SockJS cho integration test: kết nối bằng access token, đọc tin nhận được từ hàng đợi. */
public final class StompTestClient implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebSocketStompClient client;
    private final StompSession session;

    private StompTestClient(WebSocketStompClient client, StompSession session) {
        this.client = client;
        this.session = session;
    }

    public static StompTestClient connect(int port, String accessToken) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        StompSession session = client.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
        return new StompTestClient(client, session);
    }

    /** Đăng ký đích và trả hàng đợi chứa payload JSON nhận được. */
    public BlockingQueue<JsonNode> subscribe(String destination) throws InterruptedException {
        BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    queue.add(JSON.readTree((byte[]) payload));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        // SUBSCRIBE là bất đồng bộ: chờ broker ghi nhận trước khi test phát sự kiện
        Thread.sleep(300);
        return queue;
    }

    /** Tin tiếp theo trong {@code seconds} giây, hoặc lỗi nếu không có. */
    public static JsonNode next(BlockingQueue<JsonNode> queue, int seconds) throws InterruptedException {
        JsonNode node = queue.poll(seconds, TimeUnit.SECONDS);
        if (node == null) {
            throw new AssertionError("No STOMP message within " + seconds + "s");
        }
        return node;
    }

    @Override
    public void close() {
        if (session.isConnected()) {
            session.disconnect();
        }
        client.stop();
    }
}
