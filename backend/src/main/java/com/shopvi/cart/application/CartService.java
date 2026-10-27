package com.shopvi.cart.application;

import com.shopvi.cart.CartApi;
import com.shopvi.cart.CartLine;
import com.shopvi.cart.domain.CartItem;
import com.shopvi.cart.infra.CartItemRepository;
import com.shopvi.catalog.CatalogApi;
import com.shopvi.catalog.VariantSnapshot;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService implements CartApi {

    static final int MAX_ITEMS = 200;

    private final CartItemRepository repository;
    private final CatalogApi catalogApi;
    private final ShopApi shopApi;

    public CartView view(Long userId) {
        List<CartItem> items = repository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
        Map<Long, VariantSnapshot> variants = catalogApi.findVariants(items.stream().map(CartItem::getVariantId).toList());
        Map<Long, ShopSummary> shops = shopApi.findSummaries(
                variants.values().stream().map(VariantSnapshot::shopId).distinct().toList());

        // Giữ thứ tự: shop có món mới thêm gần nhất lên đầu
        Map<Long, List<CartView.Item>> byShop = new LinkedHashMap<>();
        int selectedQuantity = 0;
        long selectedSubtotal = 0;
        for (CartItem item : items) {
            VariantSnapshot v = variants.get(item.getVariantId());
            if (v == null || !shops.containsKey(v.shopId())) {
                continue; // SKU/sản phẩm đã bị xóa
            }
            boolean available = v.purchasable() && v.stock() >= item.getQuantity();
            long lineTotal = v.price() * item.getQuantity();
            if (item.isSelected() && available) {
                selectedQuantity += item.getQuantity();
                selectedSubtotal += lineTotal;
            }
            byShop.computeIfAbsent(v.shopId(), k -> new ArrayList<>()).add(new CartView.Item(item.getId(),
                    v.variantId(), v.productId(), v.productName(), v.productSlug(), v.variantName(), v.imageUrl(),
                    v.price(), item.getQuantity(), v.stock(), item.isSelected(), available, lineTotal));
        }
        List<CartView.ShopGroup> groups = byShop.entrySet().stream()
                .map(e -> new CartView.ShopGroup(shops.get(e.getKey()), e.getValue()))
                .toList();
        int itemCount = groups.stream().mapToInt(g -> g.items().size()).sum();
        return new CartView(groups, itemCount, selectedQuantity, selectedSubtotal);
    }

    public long count(Long userId) {
        return repository.countByUserId(userId);
    }

    /** Thêm SKU vào giỏ; đã có thì cộng dồn số lượng (không vượt tồn kho). */
    @Transactional
    public CartView add(Long userId, Long variantId, int quantity) {
        VariantSnapshot variant = catalogApi.findVariant(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        if (!variant.purchasable()) {
            throw new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE);
        }
        CartItem item = repository.findByUserIdAndVariantId(userId, variantId).orElse(null);
        int current = item == null ? 0 : item.getQuantity();
        ensureStock(variant, current + quantity, current);
        if (item == null) {
            if (repository.countByUserId(userId) >= MAX_ITEMS) {
                throw new BusinessException(ErrorCode.CART_LIMIT_REACHED);
            }
            repository.save(CartItem.create(userId, variantId, quantity));
        } else {
            item.changeQuantity(current + quantity);
            item.select(true);
        }
        repository.flush();
        return view(userId);
    }

    @Transactional
    public CartView update(Long userId, Long itemId, Integer quantity, Boolean selected) {
        CartItem item = getOwned(userId, itemId);
        if (quantity != null && quantity != item.getQuantity()) {
            VariantSnapshot variant = catalogApi.findVariant(item.getVariantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
            // Cho phép giảm số lượng kể cả khi đang vượt tồn kho, chỉ chặn tăng vượt
            if (quantity > item.getQuantity()) {
                ensureStock(variant, quantity, 0);
            }
            item.changeQuantity(quantity);
        }
        if (selected != null) {
            item.select(selected);
        }
        repository.flush();
        return view(userId);
    }

    /** Chọn/bỏ chọn nhiều dòng (theo shop hoặc tất cả); danh sách rỗng = toàn bộ giỏ. */
    @Transactional
    public CartView select(Long userId, Collection<Long> itemIds, boolean selected) {
        List<CartItem> items = itemIds == null || itemIds.isEmpty()
                ? repository.findByUserIdOrderByCreatedAtDescIdDesc(userId)
                : repository.findByUserIdAndIdIn(userId, itemIds);
        items.forEach(i -> i.select(selected));
        repository.flush();
        return view(userId);
    }

    @Transactional
    public CartView remove(Long userId, Collection<Long> itemIds) {
        List<CartItem> items = repository.findByUserIdAndIdIn(userId, itemIds);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        repository.deleteAll(items);
        repository.flush();
        return view(userId);
    }

    @Override
    public List<CartLine> selectedLines(Long userId) {
        return repository.findByUserIdAndSelectedTrue(userId).stream()
                .map(i -> new CartLine(i.getId(), i.getVariantId(), i.getQuantity()))
                .toList();
    }

    @Override
    @Transactional
    public void removeVariants(Long userId, Collection<Long> variantIds) {
        if (!variantIds.isEmpty()) {
            repository.deleteByUserIdAndVariantIdIn(userId, variantIds);
        }
    }

    private CartItem getOwned(Long userId, Long itemId) {
        return repository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    /** Lỗi kèm số lượng còn thêm được để FE hiển thị "Bạn chỉ có thể thêm tối đa X". */
    private static void ensureStock(VariantSnapshot variant, int wanted, int alreadyInCart) {
        if (wanted > variant.stock()) {
            int canAdd = Math.max(variant.stock() - alreadyInCart, 0);
            throw new BusinessException(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK,
                    ErrorCode.CART_QUANTITY_EXCEEDS_STOCK.getDefaultMessage(),
                    List.of(new ErrorDetail("quantity", String.valueOf(canAdd))));
        }
    }
}
