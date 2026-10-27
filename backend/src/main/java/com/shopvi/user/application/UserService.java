package com.shopvi.user.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.user.AddressSnapshot;
import com.shopvi.user.NewUser;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserApi;
import com.shopvi.user.UserCredentials;
import com.shopvi.user.UserRole;
import com.shopvi.user.domain.Address;
import com.shopvi.user.domain.User;
import com.shopvi.user.infra.AddressRepository;
import com.shopvi.user.infra.UserRepository;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserApi {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    @Override
    @Transactional
    public UserAccount create(NewUser newUser) {
        String email = normalizeEmail(newUser.email());
        String phone = normalizePhone(newUser.phone());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        User user = User.newBuyer(email, phone, newUser.passwordHash(), newUser.fullName().trim());
        return userRepository.save(user).toAccount();
    }

    @Override
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .map(u -> new UserCredentials(u.toAccount(), u.getPasswordHash()));
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        return userRepository.findById(id).map(User::toAccount);
    }

    @Override
    public Map<Long, UserAccount> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().map(User::toAccount)
                .collect(Collectors.toMap(UserAccount::id, Function.identity()));
    }

    @Override
    @Transactional
    public UserAccount promoteToSeller(Long userId) {
        User user = getProfile(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Administrators cannot open a shop");
        }
        user.promoteToSeller();
        return user.toAccount();
    }

    @Override
    public Optional<AddressSnapshot> findAddress(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId).map(Address::toSnapshot);
    }

    @Override
    public Optional<AddressSnapshot> findDefaultAddress(Long userId) {
        return addressRepository.findFirstByUserIdAndIsDefaultTrue(userId).map(Address::toSnapshot);
    }

    public User getProfile(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public User updateProfile(Long userId, String fullName, String phone, String avatarUrl) {
        User user = getProfile(userId);
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone != null && userRepository.existsByPhoneAndIdNot(normalizedPhone, userId)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        user.updateProfile(fullName.trim(), normalizedPhone, StringUtils.hasText(avatarUrl) ? avatarUrl.trim() : null);
        return user;
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }
}
