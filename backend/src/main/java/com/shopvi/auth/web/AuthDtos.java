package com.shopvi.auth.web;

import com.shopvi.auth.application.AuthService.AuthResult;
import com.shopvi.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            // BCrypt chỉ dùng 72 byte đầu của mật khẩu
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String fullName,
            @Pattern(regexp = "^(0|\\+84)\\d{9,10}$", message = "must be a valid Vietnamese phone number")
            String phone) {
    }

    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    record AuthUser(Long id, String email, String fullName, UserRole role) {
    }

    record AuthResponse(String accessToken, String tokenType, long expiresIn, AuthUser user) {
        static AuthResponse from(AuthResult result) {
            var a = result.account();
            return new AuthResponse(result.accessToken().value(), "Bearer", result.accessToken().expiresInSeconds(),
                    new AuthUser(a.id(), a.email(), a.fullName(), a.role()));
        }
    }
}
