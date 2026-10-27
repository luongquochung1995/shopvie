package com.shopvi.shop;

/** Địa chỉ lấy hàng của shop (địa giới 2 cấp). */
public record PickupAddress(String name, String phone, String provinceCode, String wardCode, String detail) {
}
