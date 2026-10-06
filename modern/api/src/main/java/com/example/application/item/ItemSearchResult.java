package com.example.application.item;

import com.example.domain.item.ItemSearchPage;
import com.example.domain.item.ItemSummary;
import java.util.List;

/** 문항 검색 결과 — 조건에 맞는 전체 건수와 현재 페이지의 행. */
public record ItemSearchResult(long count, List<Row> rows) {

    /** 검색 결과 한 행. {@code unit} 은 단원 코드, {@code tags} 는 태그 이름(태그 id 순). */
    public record Row(Integer id, String title, String unit, Integer level, List<String> tags) {

        static Row from(ItemSummary summary) {
            return new Row(summary.id(), summary.title(), summary.unitCode(), summary.level(), summary.tagNames());
        }
    }

    static ItemSearchResult from(ItemSearchPage page) {
        return new ItemSearchResult(page.total(), page.rows().stream().map(Row::from).toList());
    }
}
