package com.shopvi.promotion.web;

import com.shopvi.common.web.ApiResponse;
import com.shopvi.promotion.application.BannerService;
import com.shopvi.promotion.application.BannerService.BannerView;
import com.shopvi.promotion.domain.Banner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Banners", description = "Banner trang chủ (công khai) và quản lý banner (role ADMIN)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class BannerController {

    /** Ảnh: URL http(s). Link: URL http(s) hoặc đường dẫn trong site ("/c/thoi-trang-nam"). */
    static final String HTTP_URL = "^https?://\\S+$";
    static final String LINK = "^(https?://\\S+|/\\S*)$";

    private final BannerService bannerService;

    @Operation(summary = "Banner đang hiển thị (theo thứ tự)")
    @GetMapping("/banners")
    ApiResponse<List<BannerView>> live() {
        return ApiResponse.ok(bannerService.live());
    }

    @Operation(summary = "Tất cả banner (admin)")
    @GetMapping("/admin/banners")
    ApiResponse<List<BannerView>> all() {
        return ApiResponse.ok(bannerService.all());
    }

    @Operation(summary = "Thêm banner")
    @PostMapping("/admin/banners")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<BannerView> create(@Valid @RequestBody BannerRequest req) {
        return ApiResponse.ok(bannerService.create(req.toDetails()));
    }

    @Operation(summary = "Sửa banner")
    @PutMapping("/admin/banners/{id}")
    ApiResponse<BannerView> update(@PathVariable Long id, @Valid @RequestBody BannerRequest req) {
        return ApiResponse.ok(bannerService.update(id, req.toDetails()));
    }

    @Operation(summary = "Xóa banner")
    @DeleteMapping("/admin/banners/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        bannerService.delete(id);
    }

    record BannerRequest(@NotBlank @Size(max = 100) String title,
                         @NotBlank @Size(max = 500) @Pattern(regexp = HTTP_URL) String imageUrl,
                         @Size(max = 500) @Pattern(regexp = LINK) String linkUrl,
                         @Min(0) @Max(1000) int position, boolean active, Instant startsAt, Instant endsAt) {
        Banner.Details toDetails() {
            return new Banner.Details(title.trim(), imageUrl.trim(), linkUrl == null || linkUrl.isBlank() ? null : linkUrl.trim(),
                    position, active, startsAt, endsAt);
        }
    }
}
