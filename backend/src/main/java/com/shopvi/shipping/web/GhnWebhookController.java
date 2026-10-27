package com.shopvi.shipping.web;

import com.shopvi.shipping.application.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Shipping - Webhooks", description = "Webhook cập nhật trạng thái vận đơn từ GHN")
@RestController
@RequestMapping("/api/v1/shipping/ghn")
@RequiredArgsConstructor
class GhnWebhookController {

    private final ShippingService shippingService;

    @Operation(summary = "GHN gọi khi vận đơn đổi trạng thái; URL đăng ký với GHN kèm ?token=<app.shipping.ghn.webhook-token>",
            description = "Luôn trả 200 với bản tin hợp lệ (kể cả trùng lặp / vận đơn lạ) để GHN không gửi lại; "
                    + "401 nếu token sai.")
    @PostMapping("/webhook")
    ResponseEntity<Void> webhook(@RequestParam(required = false) String token, @RequestBody Map<String, Object> body) {
        shippingService.handleGhnWebhook(token, body);
        return ResponseEntity.ok().build();
    }
}
