package com.shopvi.search.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.search.application.SearchViews.SearchQuery;
import com.shopvi.search.application.SearchViews.SearchResult;
import com.shopvi.search.application.SearchViews.Suggestion;
import com.shopvi.search.config.SearchProperties;
import com.shopvi.search.infra.ProductIndex;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Tìm kiếm sản phẩm (P5-3). Elasticsearch lỗi/không bật → 503 SEARCH_UNAVAILABLE. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    static final int MAX_SUGGESTIONS = 8;

    private final ProductIndex index;
    private final SearchProperties properties;

    public SearchResult search(SearchQuery query) {
        requireEnabled();
        try {
            return index.search(query);
        } catch (IOException | RuntimeException ex) {
            throw unavailable(ex);
        }
    }

    public List<Suggestion> suggest(String prefix) {
        requireEnabled();
        if (!StringUtils.hasText(prefix) || prefix.trim().length() < 2) {
            return List.of();
        }
        try {
            return index.suggest(prefix, MAX_SUGGESTIONS);
        } catch (IOException | RuntimeException ex) {
            throw unavailable(ex);
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.SEARCH_UNAVAILABLE);
        }
    }

    private static BusinessException unavailable(Exception ex) {
        log.warn("Search failed: {}", ex.getMessage());
        return new BusinessException(ErrorCode.SEARCH_UNAVAILABLE);
    }
}
