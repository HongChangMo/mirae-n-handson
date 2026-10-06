package com.example.domain.item;

import java.util.List;

/** 검색 결과 한 행. {@code tagNames} 는 태그 id 순(BR-30). */
public record ItemSummary(Integer id, String title, String unitCode, Integer level, List<String> tagNames) {
}
