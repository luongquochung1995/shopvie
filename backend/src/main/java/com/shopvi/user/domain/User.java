package com.shopvi.user.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserRole;
import com.shopvi.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    public static User newBuyer(String email, String phone, String passwordHash, String fullName) {
        User user = new User();
        user.email = email;
        user.phone = phone;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.role = UserRole.BUYER;
        user.status = UserStatus.ACTIVE;
        return user;
    }

    public void updateProfile(String fullName, String phone, String avatarUrl) {
        this.fullName = fullName;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
    }

    /** Mở shop: BUYER → SELLER (SELLER giữ nguyên; ADMIN do service chặn trước). */
    public void promoteToSeller() {
        if (role == UserRole.BUYER) {
            role = UserRole.SELLER;
        }
    }

    public UserAccount toAccount() {
        return new UserAccount(getId(), email, fullName, role, status);
    }
}
