package com.shopvi.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void successResponseHasStandardShape() throws Exception {
        mvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("hello"))
                .andExpect(jsonPath("$.message").isEmpty());
    }

    @Test
    void businessExceptionMapsToErrorCodeStatus() throws Exception {
        mvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Product not found"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    void bodyValidationReturnsFieldDetails() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void typeMismatchReturnsBadRequest() throws Exception {
        mvc.perform(get("/test/number").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void unexpectedExceptionReturnsInternalErrorWithoutLeakingMessage() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Internal server error"));
    }

    record CreateRequest(@NotBlank String name) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        ApiResponse<String> ok() {
            return ApiResponse.ok("hello");
        }

        @GetMapping("/test/business")
        ApiResponse<Void> business() {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        @PostMapping("/test/validate")
        ApiResponse<String> validate(@Valid @RequestBody CreateRequest request) {
            return ApiResponse.ok(request.name());
        }

        @GetMapping("/test/number")
        ApiResponse<Integer> number(@RequestParam int n) {
            return ApiResponse.ok(n);
        }

        @GetMapping("/test/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
