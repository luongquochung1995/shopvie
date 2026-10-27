package com.shopvi.common.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopvi.common.ratelimit.RateLimiter;
import com.shopvi.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, CorsConfig.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RateLimiter rateLimiter;

    /** JWT giả lập với claim {@code role}, quyền được map bằng converter thật của ứng dụng. */
    private JwtRequestPostProcessor withRole(String role) {
        return jwt().jwt(j -> j.subject("1").claim("role", role))
                .authorities(token -> jwtAuthenticationConverter.convert(token).getAuthorities());
    }

    @Test
    void publicCatalogEndpointIsAccessibleAnonymously() throws Exception {
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void protectedEndpointReturns401InStandardFormat() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void sellerEndpointForbiddenForBuyer() throws Exception {
        mvc.perform(get("/api/v1/seller/orders").with(withRole("BUYER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void buyerCanRegisterShopButNotUseOtherSellerEndpoints() throws Exception {
        mvc.perform(post("/api/v1/seller/shop/register").with(withRole("BUYER")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/seller/shop/register"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/seller/shop").with(withRole("BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowedForAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/stats").with(withRole("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void invalidBearerTokenReturns401InStandardFormat() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("bad signature"));

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void corsPreflightAllowsFrontendOrigin() throws Exception {
        mvc.perform(options("/api/v1/me")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void corsPreflightRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/v1/me")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/products")
        ApiResponse<String> products() {
            return ApiResponse.ok("products");
        }

        @GetMapping("/api/v1/me")
        ApiResponse<String> me() {
            return ApiResponse.ok("me");
        }

        @GetMapping("/api/v1/seller/orders")
        ApiResponse<String> sellerOrders() {
            return ApiResponse.ok("orders");
        }

        @PostMapping("/api/v1/seller/shop/register")
        ApiResponse<String> registerShop() {
            return ApiResponse.ok("registered");
        }

        @GetMapping("/api/v1/seller/shop")
        ApiResponse<String> myShop() {
            return ApiResponse.ok("shop");
        }

        @GetMapping("/api/v1/admin/stats")
        ApiResponse<String> adminStats() {
            return ApiResponse.ok("stats");
        }
    }
}
