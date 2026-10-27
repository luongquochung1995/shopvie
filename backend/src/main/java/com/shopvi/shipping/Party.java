package com.shopvi.shipping;

/** Người gửi / người nhận của vận đơn. */
public record Party(String name, String phone, String provinceCode, String wardCode, String detail) {

    public Location location() {
        return new Location(provinceCode, wardCode);
    }
}
