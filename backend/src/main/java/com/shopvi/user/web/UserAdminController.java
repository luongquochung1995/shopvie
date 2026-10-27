package com.shopvi.user.web;

import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import com.shopvi.user.UserRole;
import com.shopvi.user.UserStatus;
import com.shopvi.user.application.UserAdminService;
import com.shopvi.user.application.UserAdminService.AdminUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Users", description = "Quản lý tài khoản: tìm kiếm, khóa / mở khóa (role ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Danh sách tài khoản (mới nhất trước); q tìm theo email / tên / SĐT")
    @GetMapping
    ApiResponse<PageResponse<AdminUser>> list(@RequestParam(required = false) @Size(max = 100) String q,
                                              @RequestParam(required = false) UserRole role,
                                              @RequestParam(required = false) UserStatus status,
                                              @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Pageable newest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return ApiResponse.ok(PageResponse.from(userAdminService.list(q, role, status, newest)));
    }

    @Operation(summary = "Khóa tài khoản kèm lý do: thu hồi mọi phiên đăng nhập, không đăng nhập được nữa")
    @PostMapping("/{id}/lock")
    ApiResponse<AdminUser> lock(@PathVariable Long id, @Valid @RequestBody LockRequest req) {
        return ApiResponse.ok(userAdminService.lock(id, req.reason()));
    }

    @Operation(summary = "Mở khóa tài khoản")
    @PostMapping("/{id}/unlock")
    ApiResponse<AdminUser> unlock(@PathVariable Long id) {
        return ApiResponse.ok(userAdminService.unlock(id));
    }

    record LockRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
