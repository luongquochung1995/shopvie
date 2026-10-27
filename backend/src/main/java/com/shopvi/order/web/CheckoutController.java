package com.shopvi.order.web;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.order.application.CheckoutService;
import com.shopvi.order.application.CheckoutViews.CheckoutPayment;
import com.shopvi.order.application.CheckoutViews.PlaceOrderCommand;
import com.shopvi.order.application.CheckoutViews.PlaceOrderResult;
import com.shopvi.order.application.CheckoutViews.Preview;
import com.shopvi.payment.PaymentLink;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Checkout", description = "Thanh toán các món đã chọn trong giỏ (tách đơn theo shop)")
@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
class CheckoutController {

    private final CheckoutService checkoutService;

    @Operation(summary = "Xem trước đơn hàng: nhóm theo shop, tạm tính, phí vận chuyển, tổng tiền",
            description = "Không truyền addressId thì dùng địa chỉ mặc định. Phí ship tính theo địa chỉ lấy hàng của "
                    + "shop và địa chỉ nhận (GHN hoặc bảng phí nội bộ). paymentMethods = COD + cổng online đã cấu hình.")
    @PostMapping("/preview")
    ApiResponse<Preview> preview(@CurrentUserId Long userId, @RequestBody(required = false) PreviewRequest req) {
        return ApiResponse.ok(checkoutService.preview(userId, req == null ? null : req.addressId()));
    }

    @Operation(summary = "Đặt hàng: tách đơn theo shop, trừ kho, xóa món đã đặt khỏi giỏ",
            description = "expectedTotal = tổng tiền ở bước preview; khác thực tế → 409 PRICE_CHANGED. "
                    + "VNPAY/MOMO: trả paymentUrl để chuyển người mua sang cổng thanh toán; quá hạn thanh toán "
                    + "(paymentExpiresAt) đơn bị hủy và hoàn kho.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PlaceOrderResult> placeOrder(@CurrentUserId Long userId, @Valid @RequestBody PlaceOrderRequest req,
                                             HttpServletRequest http) {
        return ApiResponse.ok(checkoutService.placeOrder(userId,
                new PlaceOrderCommand(req.addressId(), req.paymentMethod(), req.notes(), req.expectedTotal()),
                http.getRemoteAddr()));
    }

    @Operation(summary = "Trạng thái thanh toán của checkout (trang kết quả thanh toán hỏi lại tới khi có kết quả)")
    @GetMapping("/{checkoutId}/payment")
    ApiResponse<CheckoutPayment> paymentStatus(@CurrentUserId Long userId, @PathVariable UUID checkoutId) {
        return ApiResponse.ok(checkoutService.paymentStatus(userId, checkoutId));
    }

    @Operation(summary = "Thanh toán lại các đơn online chưa thanh toán của checkout (trong thời hạn)",
            description = "409 PAYMENT_NOT_REQUIRED nếu không còn đơn chờ thanh toán; 422 PAYMENT_EXPIRED nếu quá hạn.")
    @PostMapping("/{checkoutId}/payment")
    ApiResponse<PaymentLink> retryPayment(@CurrentUserId Long userId, @PathVariable UUID checkoutId,
                                          HttpServletRequest http) {
        return ApiResponse.ok(checkoutService.retryPayment(userId, checkoutId, http.getRemoteAddr()));
    }

    record PreviewRequest(Long addressId) {
    }

    record PlaceOrderRequest(@NotNull Long addressId, @NotBlank String paymentMethod, Map<Long, String> notes,
                             @PositiveOrZero Long expectedTotal) {
    }
}
