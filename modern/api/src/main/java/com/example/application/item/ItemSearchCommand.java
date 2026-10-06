package com.example.application.item;

/**
 * 문항 검색 요청 값. 레거시 {@code search.php} 의 GET 파라미터와 이름이 같다.
 * 각 값은 파라미터가 없으면 {@code null}, 비어 있으면 {@code ""} — 해석은 도메인이 한다.
 */
public record ItemSearchCommand(
    String q,
    String unit,
    String level,
    String tag,
    String sort,
    String dir,
    String page) {
}
