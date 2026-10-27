package com.shopvi.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.user.UserRole;
import com.shopvi.user.UserStatus;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void lockAndUnlockKeepReasonOnlyWhileLocked() {
        User user = User.newBuyer("a@example.com", null, "hash", "A");
        user.lock("Spam");
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockReason()).isEqualTo("Spam");
        assertThatThrownBy(() -> user.lock("again")).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_USER_STATUS);

        user.unlock();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLockReason()).isNull();
    }

    @Test
    void adminCannotBeLocked() throws ReflectiveOperationException {
        User admin = User.newBuyer("admin@example.com", null, "hash", "Admin");
        Field role = User.class.getDeclaredField("role");
        role.setAccessible(true);
        role.set(admin, UserRole.ADMIN);
        assertThatThrownBy(() -> admin.lock("x")).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_LOCK_ADMIN);
    }
}
