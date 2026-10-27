package com.shopvi.user;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * API công khai của module user cho các module khác.
 */
public interface UserApi {

    /**
     * Tạo tài khoản BUYER. Ném {@code EMAIL_ALREADY_EXISTS}/{@code PHONE_ALREADY_EXISTS} nếu trùng.
     */
    UserAccount create(NewUser newUser);

    Optional<UserCredentials> findCredentialsByEmail(String email);

    Optional<UserAccount> findById(Long id);

    /** Id không tồn tại sẽ không có trong map. */
    Map<Long, UserAccount> findByIds(Collection<Long> ids);

    /**
     * Nâng tài khoản BUYER thành SELLER khi mở shop (SELLER giữ nguyên). Ném {@code USER_NOT_FOUND} nếu không có user,
     * {@code FORBIDDEN} nếu là ADMIN. Access token cũ vẫn mang role cũ tới khi được làm mới.
     */
    UserAccount promoteToSeller(Long userId);

    /** Địa chỉ thuộc user (không thuộc → empty). */
    Optional<AddressSnapshot> findAddress(Long userId, Long addressId);

    Optional<AddressSnapshot> findDefaultAddress(Long userId);
}
