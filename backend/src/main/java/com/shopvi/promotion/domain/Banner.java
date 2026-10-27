package com.shopvi.promotion.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Banner trang chủ (admin quản lý); hiển thị khi đang bật và trong khoảng thời gian (nếu có). */
@Getter
@Entity
@Table(name = "banners")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "link_url")
    private String linkUrl;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    public static Banner create(Details d) {
        Banner b = new Banner();
        b.update(d);
        return b;
    }

    public void update(Details d) {
        title = d.title();
        imageUrl = d.imageUrl();
        linkUrl = d.linkUrl();
        position = d.position();
        active = d.active();
        startsAt = d.startsAt();
        endsAt = d.endsAt();
    }

    public boolean isLive(Instant now) {
        return active && (startsAt == null || !now.isBefore(startsAt)) && (endsAt == null || now.isBefore(endsAt));
    }

    public record Details(String title, String imageUrl, String linkUrl, int position, boolean active, Instant startsAt,
                          Instant endsAt) {
    }
}
