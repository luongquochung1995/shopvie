package com.shopvi.user.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
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

    /** Lý do admin khóa (chỉ có khi LOCKED). */
    @Column(name = "lock_reason")
    private String lockReason;

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

    /** Admin khóa tài khoản vi phạm: không đăng nhập / làm mới phiên được nữa. Không khóa tài khoản ADMIN. */
    public void lock(String reason) {
        if (role == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_LOCK_ADMIN);
        }
        if (status == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.INVALID_USER_STATUS);
        }
        status = UserStatus.LOCKED;
        lockReason = reason;
    }

    public void unlock() {
        if (status != UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.INVALID_USER_STATUS);
        }
        status = UserStatus.ACTIVE;
        lockReason = null;
    }

    public UserAccount toAccount() {
        return new UserAccount(getId(), email, fullName, role, status);
    }
}
