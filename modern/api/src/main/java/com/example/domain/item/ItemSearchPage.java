package com.example.domain.item;

import java.util.List;

/** 검색 결과 — 조건에 맞는 전체 건수와 현재 페이지의 행. */
public record ItemSearchPage(long total, List<ItemSummary> rows) {
}
