package com.shopvi.catalog.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Danh mục nhiều cấp: level 0 = gốc, tối đa {@value #MAX_LEVEL}.
 */
@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    public static final int MAX_LEVEL = 2;

    @Column(name = "parent_id", updatable = false)
    private Long parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, updatable = false)
    private short level;

    @Column(nullable = false)
    private int position;

    @Column(name = "icon_url")
    private String iconUrl;

    public static Category create(Long parentId, int level, String name, String slug, int position, String iconUrl) {
        Category category = new Category();
        category.parentId = parentId;
        category.level = (short) level;
        category.update(name, slug, position, iconUrl);
        return category;
    }

    public void update(String name, String slug, int position, String iconUrl) {
        this.name = name;
        this.slug = slug;
        this.position = position;
        this.iconUrl = iconUrl;
    }
}
