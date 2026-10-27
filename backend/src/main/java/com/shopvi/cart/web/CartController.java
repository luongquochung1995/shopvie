package com.shopvi.cart.web;

import com.shopvi.cart.application.CartService;
import com.shopvi.cart.application.CartView;
import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart", description = "Giỏ hàng của người dùng đang đăng nhập (nhóm theo shop)")
@Validated
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
class CartController {

    private static final int MAX_QUANTITY = 999;

    private final CartService cartService;

    @Operation(summary = "Xem giỏ hàng nhóm theo shop, kèm tổng tiền các món đã chọn")
    @GetMapping
    ApiResponse<CartView> view(@CurrentUserId Long userId) {
        return ApiResponse.ok(cartService.view(userId));
    }

    @Operation(summary = "Số món (SKU) trong giỏ — cho badge trên header")
    @GetMapping("/count")
    ApiResponse<Map<String, Long>> count(@CurrentUserId Long userId) {
        return ApiResponse.ok(Map.of("count", cartService.count(userId)));
    }

    @Operation(summary = "Thêm SKU vào giỏ (cộng dồn nếu đã có, không vượt tồn kho)")
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CartView> add(@CurrentUserId Long userId, @Valid @RequestBody AddItemRequest req) {
        return ApiResponse.ok(cartService.add(userId, req.variantId(), req.quantity()));
    }

    @Operation(summary = "Đổi số lượng và/hoặc trạng thái chọn của một dòng")
    @PatchMapping("/items/{id}")
    ApiResponse<CartView> update(@CurrentUserId Long userId, @PathVariable Long id,
                                 @Valid @RequestBody UpdateItemRequest req) {
        return ApiResponse.ok(cartService.update(userId, id, req.quantity(), req.selected()));
    }

    @Operation(summary = "Chọn/bỏ chọn nhiều dòng (theo shop hoặc tất cả khi itemIds rỗng)")
    @PatchMapping("/selection")
    ApiResponse<CartView> select(@CurrentUserId Long userId, @Valid @RequestBody SelectionRequest req) {
        return ApiResponse.ok(cartService.select(userId, req.itemIds(), req.selected()));
    }

    @Operation(summary = "Xóa một dòng khỏi giỏ")
    @DeleteMapping("/items/{id}")
    ApiResponse<CartView> remove(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.ok(cartService.remove(userId, List.of(id)));
    }

    @Operation(summary = "Xóa nhiều dòng khỏi giỏ (?ids=1,2,3)")
    @DeleteMapping("/items")
    ApiResponse<CartView> removeMany(@CurrentUserId Long userId, @RequestParam @NotEmpty List<Long> ids) {
        return ApiResponse.ok(cartService.remove(userId, ids));
    }

    record AddItemRequest(@NotNull Long variantId, @Min(1) @Max(MAX_QUANTITY) int quantity) {
    }

    record UpdateItemRequest(@Min(1) @Max(MAX_QUANTITY) Integer quantity, Boolean selected) {
    }

    record SelectionRequest(List<Long> itemIds, @NotNull Boolean selected) {
    }
}
