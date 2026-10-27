package com.shopvi.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== Generic =====
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    CONFLICT(HttpStatus.CONFLICT, "Resource conflict"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large"),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),

    // ===== Auth =====
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already registered"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Phone number is already registered"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account is locked"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"),

    // ===== User =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "Address not found"),
    ADDRESS_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "Address book is full"),

    // ===== Shop =====
    SHOP_NOT_FOUND(HttpStatus.NOT_FOUND, "Shop not found"),
    SHOP_ALREADY_EXISTS(HttpStatus.CONFLICT, "You already have a shop"),
    SHOP_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Shop is not active yet"),
    INVALID_SHOP_STATUS(HttpStatus.CONFLICT, "Shop status cannot be changed this way"),

    // ===== Catalog =====
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Category not found"),
    CATEGORY_SLUG_EXISTS(HttpStatus.CONFLICT, "Category slug already exists"),
    CATEGORY_TOO_DEEP(HttpStatus.UNPROCESSABLE_ENTITY, "Category tree supports at most 3 levels"),
    CATEGORY_NOT_EMPTY(HttpStatus.CONFLICT, "Category still has sub-categories or products"),
    CATEGORY_NOT_LEAF(HttpStatus.UNPROCESSABLE_ENTITY, "Products must belong to a lowest-level category"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    PRODUCT_BANNED(HttpStatus.UNPROCESSABLE_ENTITY, "Product has been banned by an administrator"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product variant not found"),
    SKU_ALREADY_EXISTS(HttpStatus.CONFLICT, "SKU is already in use"),
    PRODUCT_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Product is no longer available"),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "Not enough stock"),

    // ===== Cart =====
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Cart item not found"),
    CART_QUANTITY_EXCEEDS_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "Quantity exceeds available stock"),
    CART_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "Cart is full"),

    // ===== Checkout & Order =====
    NO_ITEMS_SELECTED(HttpStatus.UNPROCESSABLE_ENTITY, "No cart items selected for checkout"),
    PRICE_CHANGED(HttpStatus.CONFLICT, "Prices have changed, please review your order"),
    PAYMENT_METHOD_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "Payment method is not supported"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.CONFLICT, "Order status cannot be changed this way"),
    ORDER_AWAITING_PAYMENT(HttpStatus.CONFLICT, "Order has not been paid yet"),

    // ===== Payment & Shipping =====
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment not found"),
    PAYMENT_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Payment window has expired"),
    PAYMENT_NOT_REQUIRED(HttpStatus.CONFLICT, "These orders do not need online payment"),
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Payment provider is unavailable, please try again"),
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "Invalid signature"),
    SHIPPING_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Shipping provider is unavailable, please try again"),
    INVALID_ADDRESS(HttpStatus.UNPROCESSABLE_ENTITY, "Province or ward code is invalid"),

    // ===== Promotion =====
    VOUCHER_NOT_FOUND(HttpStatus.NOT_FOUND, "Voucher not found"),
    VOUCHER_CODE_EXISTS(HttpStatus.CONFLICT, "Voucher code already exists"),
    VOUCHER_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Voucher cannot be applied to this order"),
    VOUCHER_EXHAUSTED(HttpStatus.CONFLICT, "Voucher has run out"),
    FLASH_SALE_NOT_FOUND(HttpStatus.NOT_FOUND, "Flash sale not found"),
    FLASH_SALE_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Flash sale is not running"),
    FLASH_SALE_SOLD_OUT(HttpStatus.CONFLICT, "Flash sale item is sold out"),
    FLASH_SALE_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "Flash sale purchase limit reached"),
    FLASH_SALE_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Flash sale reservation not found"),
    FLASH_SALE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Flash sale is temporarily unavailable"),
    INVALID_PRODUCT_STATUS(HttpStatus.CONFLICT, "Product status does not allow this action"),
    INVALID_USER_STATUS(HttpStatus.CONFLICT, "User status does not allow this action"),
    CANNOT_LOCK_ADMIN(HttpStatus.UNPROCESSABLE_ENTITY, "Administrator accounts cannot be locked"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found"),
    CHAT_WITH_OWN_SHOP(HttpStatus.UNPROCESSABLE_ENTITY, "You cannot chat with your own shop"),
    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "Banner not found"),

    // ===== Review =====
    REVIEW_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Only items of completed orders can be reviewed"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "This item has already been reviewed"),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "Review not found"),
    REVIEW_ALREADY_REPLIED(HttpStatus.CONFLICT, "This review has already been replied to"),

    // ===== Search =====
    SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Search is temporarily unavailable"),

    // ===== Media =====
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPG, PNG and WEBP images are allowed"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File must not exceed 5MB"),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable");

    private final HttpStatus status;
    private final String defaultMessage;
}
