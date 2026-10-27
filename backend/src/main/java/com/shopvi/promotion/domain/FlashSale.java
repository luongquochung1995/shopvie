package com.shopvi.promotion.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/** Phiên flash sale do admin tạo. */
@Getter
@Entity
@Table(name = "flash_sales")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashSale extends BaseEntity {

    public enum Status { ACTIVE, CANCELLED }

    @Column(nullable = false)
    private String name;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "flashSale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<FlashSaleItem> items = new ArrayList<>();

    public static FlashSale create(String name, Instant startAt, Instant endAt) {
        FlashSale s = new FlashSale();
        s.name = name;
        s.startAt = startAt;
        s.endAt = endAt;
        s.status = Status.ACTIVE;
        return s;
    }

    public FlashSaleItem addItem(Long variantId, Long productId, long salePrice, int quota, int perUserLimit) {
        FlashSaleItem item = FlashSaleItem.of(this, variantId, productId, salePrice, quota, perUserLimit);
        items.add(item);
        return item;
    }

    public void cancel() {
        status = Status.CANCELLED;
    }

    public boolean isRunning(Instant now) {
        return status == Status.ACTIVE && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public List<FlashSaleItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
