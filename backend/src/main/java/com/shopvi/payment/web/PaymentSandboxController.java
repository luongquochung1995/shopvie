package com.shopvi.payment.web;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.payment.application.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cổng thanh toán giả lập cho dev/test — chỉ hoạt động khi {@code app.payment.sandbox-gateway=true}. */
@Tag(name = "Payments - Sandbox", description = "Cổng thanh toán giả lập (chỉ dev/test)")
@RestController
@RequestMapping("/api/v1/payments/sandbox")
@RequiredArgsConstructor
class PaymentSandboxController {

    private final PaymentService paymentService;

    @Operation(summary = "Giả lập kết quả trên cổng: trả return URL đã ký để trình duyệt chuyển tới",
            description = "404 nếu không bật sandbox gateway.")
    @PostMapping("/{reference}")
    ApiResponse<SandboxResult> complete(@CurrentUserId Long userId, @PathVariable String reference,
                                        @Valid @RequestBody SandboxRequest req) {
        return ApiResponse.ok(new SandboxResult(paymentService.sandboxCallbackUrl(userId, reference, req.success())));
    }

    record SandboxRequest(@NotNull Boolean success) {
    }

    record SandboxResult(String redirectUrl) {
    }
}
