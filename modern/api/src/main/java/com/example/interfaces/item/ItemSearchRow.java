package com.example.interfaces.item;

import com.example.application.item.ItemSearchResult;
import java.util.List;

/**
 * 문항 검색 결과 한 행. 필드는 레거시 결과 표의 열(id, title, unit, level, tags)과 같다.
 * 근거: {@code legacy/item-bank-php/search.php:674-678}.
 */
public record ItemSearchRow(Integer id, String title, String unit, Integer level, List<String> tags) {

    static ItemSearchRow from(ItemSearchResult.Row row) {
        return new ItemSearchRow(row.id(), row.title(), row.unit(), row.level(), row.tags());
    }
}
