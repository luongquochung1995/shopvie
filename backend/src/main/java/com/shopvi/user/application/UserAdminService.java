package com.shopvi.user.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.user.UserLocked;
import com.shopvi.user.UserRole;
import com.shopvi.user.UserStatus;
import com.shopvi.user.domain.User;
import com.shopvi.user.infra.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Admin quản lý tài khoản: tìm theo email/tên/SĐT, lọc vai trò/trạng thái, khóa / mở khóa. */
@Service
@RequiredArgsConstructor
@Transactional
public class UserAdminService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<AdminUser> list(String keyword, UserRole role, UserStatus status, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("fullName")), like, '\\'), cb.like(root.get("phone"), like, '\\')));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return userRepository.findAll(spec, pageable).map(AdminUser::of);
    }

    public AdminUser lock(Long userId, String reason) {
        User user = get(userId);
        user.lock(reason.trim());
        userRepository.flush();
        events.publishEvent(new UserLocked(userId));
        return AdminUser.of(user);
    }

    public AdminUser unlock(Long userId) {
        User user = get(userId);
        user.unlock();
        userRepository.flush();
        return AdminUser.of(user);
    }

    private User get(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public record AdminUser(Long id, String email, String phone, String fullName, String avatarUrl, UserRole role,
                            UserStatus status, String lockReason, Instant createdAt) {
        static AdminUser of(User u) {
            return new AdminUser(u.getId(), u.getEmail(), u.getPhone(), u.getFullName(), u.getAvatarUrl(), u.getRole(),
                    u.getStatus(), u.getLockReason(), u.getCreatedAt());
        }
    }
}
