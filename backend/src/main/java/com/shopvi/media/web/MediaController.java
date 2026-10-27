package com.shopvi.media.web;

import com.shopvi.common.web.ApiResponse;
import com.shopvi.media.application.MediaService;
import com.shopvi.media.application.MediaService.UploadedMedia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Media", description = "Upload ảnh lên object storage")
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "Upload 1 ảnh (JPG/PNG/WEBP, tối đa 5MB), trả về URL public")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<UploadedMedia> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(mediaService.uploadImage(file));
    }
}
