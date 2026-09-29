package com.example.interfaces.item;

import com.example.application.item.ItemSearchResult;
import java.util.List;

/**
 * 문항 검색 응답 {@code {rows, count, message}}.
 * 건수가 0이면 레거시와 같은 안내 문구를, 아니면 {@code null} 을 둔다(BR-28, {@code search.php:657-658}).
 */
public record ItemSearchResponse(List<ItemSearchRow> rows, long count, String message) {

    static final String NO_RESULT_MESSAGE = "검색 결과가 없습니다";

    static ItemSearchResponse from(ItemSearchResult result) {
        return new ItemSearchResponse(
            result.rows().stream().map(ItemSearchRow::from).toList(),
            result.count(),
            result.count() == 0 ? NO_RESULT_MESSAGE : null);
    }
}
