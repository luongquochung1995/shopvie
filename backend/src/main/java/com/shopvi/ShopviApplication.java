package com.shopvi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Tắt in-memory user mặc định của Spring Security; xác thực JWT sẽ được thêm ở Phase 1 (P1-1).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ShopviApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopviApplication.class, args);
    }
}
