package com.shopvi.order.domain;

import com.shopvi.user.AddressSnapshot;

/**
 * Snapshot địa chỉ giao hàng lúc đặt (JSONB); không đổi khi người dùng sửa sổ địa chỉ.
 */
public record ShippingAddress(String receiverName, String phone, String provinceCode, String districtCode,
                              String wardCode, String detail) {

    public static ShippingAddress of(AddressSnapshot a) {
        return new ShippingAddress(a.receiverName(), a.phone(), a.provinceCode(), a.districtCode(), a.wardCode(),
                a.detail());
    }
}
