package com.shopvi.user;

/**
 * Địa chỉ giao hàng dùng cho checkout; được chụp lại (snapshot) vào đơn hàng.
 */
public record AddressSnapshot(Long id, String receiverName, String phone, String provinceCode, String districtCode,
                              String wardCode, String detail) {
}
