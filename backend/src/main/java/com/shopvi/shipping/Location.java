package com.shopvi.shipping;

/** Vị trí theo địa giới 2 cấp: mã tỉnh/thành và mã xã/phường (bộ mã provinces.open-api.vn v2). */
public record Location(String provinceCode, String wardCode) {
}
