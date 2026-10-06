package com.example.domain.item;

/**
 * 문항 검색 조건. 요청 파라미터를 레거시 {@code search.php} 와 똑같이 해석해 만든다.
 * 입력이 틀려도 거부하지 않는다 — 레거시는 경고만 띄우고 조회했다(BR-09, BR-12, BR-16, BR-18, BR-20, BR-26).
 *
 * @param keyword   제목 · 지문 부분 일치 키워드. 없으면 {@code null}
 * @param unitCode  단원 코드 정확 일치. 없으면 {@code null}
 * @param level     난이도 조건
 * @param tag       태그 이름 정확 일치. 없으면 {@code null}
 * @param sortOrder 정렬
 * @param page      1 ~ {@link #MAX_PAGE}
 */
public record ItemSearchCondition(
    String keyword,
    String unitCode,
    LevelFilter level,
    String tag,
    ItemSortOrder sortOrder,
    int page) {

    /** 키워드 최대 글자 수 — {@code search.php:86}. */
    public static final int KEYWORD_MAX_LENGTH = 100;
    /** 태그 이름 최대 글자 수 — {@code search.php:240}. */
    public static final int TAG_MAX_LENGTH = 50;
    /** 난이도 파라미터가 비었을 때의 상한(미포함) — {@code search.php:215}. 난이도 5 문항이 빠진다. */
    public static final int DEFAULT_LEVEL_EXCLUSIVE_MAX = 5;
    /** 페이지 크기 — {@code search.php:349, :523}. */
    public static final int PAGE_SIZE = 20;
    /** 페이지 번호 상한 — {@code search.php:345}. */
    public static final int MAX_PAGE = 999;

    private static final String PAGE_PATTERN = "[0-9]+";

    /**
     * 요청 파라미터 → 검색 조건. 각 값은 파라미터가 없으면 {@code null}, 비어 있으면 {@code ""}.
     */
    public static ItemSearchCondition of(
        String q, String unit, String level, String tag, String sort, String dir, String page) {
        return new ItemSearchCondition(
            keyword(nullToEmpty(q)),
            emptyToNull(PhpStrings.trim(nullToEmpty(unit))),
            levelFilter(PhpStrings.trim(nullToEmpty(level))),
            tagName(nullToEmpty(tag)),
            ItemSortOrder.resolve(nullToEmpty(sort), nullToEmpty(dir)),
            pageNumber(page == null ? "1" : page));
    }

    /** 현재 페이지의 첫 행 위치. */
    public int offset() {
        return (page - 1) * PAGE_SIZE;
    }

    /** BR-04, BR-05 — trim 후 100자까지. % · _ 는 그대로 둔다(BR-06). */
    private static String keyword(String raw) {
        String q = PhpStrings.trim(raw);
        if (PhpStrings.mbLength(q) > KEYWORD_MAX_LENGTH) {
            q = PhpStrings.mbSubstr(q, KEYWORD_MAX_LENGTH);
        }
        return emptyToNull(q);
    }

    /**
     * BR-10 ~ BR-12 — 비면 5 미만, 그 밖은 (int) 변환 값과 같은 난이도.
     * 레거시는 1~5 한 자리({@code search.php:217})와 그 밖({@code :223})을 나누지만 차이는 경고 표시뿐이라 합쳤다.
     */
    private static LevelFilter levelFilter(String level) {
        if (level.isEmpty()) {
            return LevelFilter.belowMax(DEFAULT_LEVEL_EXCLUSIVE_MAX);
        }
        return LevelFilter.equalTo(PhpStrings.toInt(level));
    }

    /** BR-13, BR-14 — trim 후 50자까지. */
    private static String tagName(String raw) {
        String tag = PhpStrings.trim(raw);
        if (PhpStrings.mbLength(tag) > TAG_MAX_LENGTH) {
            tag = PhpStrings.mbSubstr(tag, TAG_MAX_LENGTH);
        }
        return emptyToNull(tag);
    }

    /** BR-26, BR-27 — trim 하지 않는다. 숫자만이면 그 값, 아니면 1. 1 미만은 1, 999 초과는 999. */
    private static int pageNumber(String raw) {
        long page = 1;
        if (PhpStrings.matchesLine(PAGE_PATTERN, raw)) {
            page = PhpStrings.toInt(raw);
        }
        if (page < 1) {
            page = 1;
        }
        if (page > MAX_PAGE) {
            page = MAX_PAGE;
        }
        return (int) page;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
