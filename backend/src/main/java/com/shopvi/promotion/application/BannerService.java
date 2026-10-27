package com.shopvi.promotion.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.promotion.domain.Banner;
import com.shopvi.promotion.infra.BannerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Banner trang chủ: danh sách công khai (đang hiển thị) và quản lý của admin. */
@Service
@RequiredArgsConstructor
@Transactional
public class BannerService {

    public static final String CACHE = "banners";

    private final BannerRepository repository;
    private final Clock clock;

    @Cacheable(cacheNames = CACHE, key = "'live'")
    @Transactional(readOnly = true)
    public List<BannerView> live() {
        Instant now = clock.instant();
        return repository.findByActiveTrueOrderByPositionAscIdAsc().stream().filter(b -> b.isLive(now)).map(BannerView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BannerView> all() {
        return repository.findAllByOrderByPositionAscIdAsc().stream().map(BannerView::of).toList();
    }

    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public BannerView create(Banner.Details d) {
        validate(d);
        return BannerView.of(repository.save(Banner.create(d)));
    }

    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public BannerView update(Long id, Banner.Details d) {
        validate(d);
        Banner b = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND));
        b.update(d);
        repository.flush();
        return BannerView.of(b);
    }

    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public void delete(Long id) {
        Banner b = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.BANNER_NOT_FOUND));
        repository.delete(b);
    }

    private static void validate(Banner.Details d) {
        List<ErrorDetail> errors = new ArrayList<>();
        if (d.startsAt() != null && d.endsAt() != null && !d.endsAt().isAfter(d.startsAt())) {
            errors.add(new ErrorDetail("endsAt", "must be after startsAt"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), errors);
        }
    }

    public record BannerView(Long id, String title, String imageUrl, String linkUrl, int position, boolean active,
                             Instant startsAt, Instant endsAt) {
        static BannerView of(Banner b) {
            return new BannerView(b.getId(), b.getTitle(), b.getImageUrl(), b.getLinkUrl(), b.getPosition(), b.isActive(),
                    b.getStartsAt(), b.getEndsAt());
        }
    }
}
