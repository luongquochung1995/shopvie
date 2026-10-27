package com.shopvi.payment.web;

import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.application.PaymentService;
import com.shopvi.payment.application.PaymentService.ConfirmResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint cổng thanh toán gọi về (public, xác thực bằng chữ ký). Return URL (trình duyệt) và IPN (server-to-server)
 * dùng chung logic xác nhận idempotent; return URL chuyển người mua về trang kết quả trên frontend.
 */
@Tag(name = "Payments - Callbacks", description = "Return URL và IPN của VNPay / MoMo (xác thực bằng chữ ký)")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
class PaymentCallbackController {

    private final PaymentService paymentService;

    @Operation(summary = "VNPay return URL: xác nhận kết quả rồi chuyển về trang kết quả thanh toán (302)")
    @GetMapping("/vnpay/return")
    ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        return redirect(paymentService.confirm(PaymentChannel.VNPAY, params));
    }

    @Operation(summary = "VNPay IPN: trả RspCode theo đặc tả VNPay",
            description = "00 thành công · 01 không tìm thấy giao dịch · 02 đã xác nhận trước đó · 04 sai số tiền · "
                    + "97 sai chữ ký")
    @GetMapping("/vnpay/ipn")
    Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        ConfirmResult result = paymentService.confirm(PaymentChannel.VNPAY, params);
        return switch (result.outcome()) {
            case CONFIRMED -> Map.of("RspCode", "00", "Message", "Confirm Success");
            case ALREADY_CONFIRMED -> Map.of("RspCode", "02", "Message", "Order already confirmed");
            case NOT_FOUND -> Map.of("RspCode", "01", "Message", "Order not found");
            case INVALID_AMOUNT -> Map.of("RspCode", "04", "Message", "Invalid amount");
            case INVALID_SIGNATURE -> Map.of("RspCode", "97", "Message", "Invalid signature");
        };
    }

    @Operation(summary = "MoMo redirect URL: xác nhận kết quả rồi chuyển về trang kết quả thanh toán (302)")
    @GetMapping("/momo/return")
    ResponseEntity<Void> momoReturn(@RequestParam Map<String, String> params) {
        return redirect(paymentService.confirm(PaymentChannel.MOMO, params));
    }

    @Operation(summary = "MoMo IPN: 204 khi đã ghi nhận (kể cả gọi lặp), 400 nếu sai chữ ký / số tiền")
    @PostMapping("/momo/ipn")
    ResponseEntity<Void> momoIpn(@RequestBody Map<String, Object> body) {
        Map<String, String> params = new HashMap<>();
        body.forEach((k, v) -> params.put(k, v == null ? "" : String.valueOf(v)));
        ConfirmResult result = paymentService.confirm(PaymentChannel.MOMO, params);
        return switch (result.outcome()) {
            case CONFIRMED, ALREADY_CONFIRMED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            case INVALID_AMOUNT, INVALID_SIGNATURE -> ResponseEntity.badRequest().build();
        };
    }

    private ResponseEntity<Void> redirect(ConfirmResult result) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(paymentService.frontendResultUrl(result)))
                .build();
    }
}
