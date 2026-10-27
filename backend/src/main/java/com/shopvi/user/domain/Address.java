package com.shopvi.user.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.user.AddressSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "addresses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(nullable = false)
    private String phone;

    @Column(name = "province_code", nullable = false)
    private String provinceCode;

    /** Địa giới 2 cấp từ 07/2025 không còn huyện; chỉ dùng cho dữ liệu cũ (V3). */
    @Column(name = "district_code")
    private String districtCode;

    @Column(name = "ward_code", nullable = false)
    private String wardCode;

    @Column(nullable = false)
    private String detail;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public static Address create(Long userId, AddressData data) {
        Address address = new Address();
        address.userId = userId;
        address.apply(data);
        return address;
    }

    public void apply(AddressData data) {
        this.receiverName = data.receiverName();
        this.phone = data.phone();
        this.provinceCode = data.provinceCode();
        this.districtCode = data.districtCode();
        this.wardCode = data.wardCode();
        this.detail = data.detail();
    }

    public AddressSnapshot toSnapshot() {
        return new AddressSnapshot(getId(), receiverName, phone, provinceCode, districtCode, wardCode, detail);
    }

    public void markDefault(boolean value) {
        this.isDefault = value;
    }

    /** Dữ liệu địa chỉ người dùng nhập. */
    public record AddressData(String receiverName, String phone, String provinceCode, String districtCode,
                              String wardCode, String detail) {
    }
}
