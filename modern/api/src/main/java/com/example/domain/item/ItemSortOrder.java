package com.example.domain.item;

/**
 * 문항 검색 정렬. ORDER BY 는 이 목록의 고정 문자열로만 만든다(화이트리스트).
 * 별칭: {@code i} = 문항, {@code u} = 단원. 근거: {@code legacy/item-bank-php/search.php:276-327}.
 */
public enum ItemSortOrder {

    /** 정렬 기준이 비었거나 알 수 없을 때 — 어려운 문항부터, 같은 난이도면 번호 순 (BR-17, BR-18). */
    DEFAULT("i.level desc, i.id asc"),
    ID_ASC("i.id asc"),
    ID_DESC("i.id desc"),
    TITLE_ASC("i.title asc, i.id asc"),
    TITLE_DESC("i.title desc, i.id asc"),
    /** 2차 키 level DESC 는 방향과 무관하게 고정 (BR-23). */
    UNIT_ASC("u.code asc, i.level desc, i.id asc"),
    UNIT_DESC("u.code desc, i.level desc, i.id asc"),
    LEVEL_ASC("i.level asc, i.id asc"),
    LEVEL_DESC("i.level desc, i.id asc"),
    CREATED_ASC("i.createdAt asc, i.id asc"),
    CREATED_DESC("i.createdAt desc, i.id asc");

    private final String jpql;

    ItemSortOrder(String jpql) {
        this.jpql = jpql;
    }

    /** ORDER BY 뒤에 붙일 JPQL 조각. */
    public String jpql() {
        return jpql;
    }

    /**
     * 정렬 기준 · 방향 해석 (BR-19 ~ BR-21). 둘 다 trim · 소문자로 바꾼 뒤 판단한다.
     * {@code id} · {@code title} · {@code unit} 은 {@code desc} 일 때만 내림차순,
     * {@code level} · {@code created} 는 {@code asc} 일 때만 오름차순.
     */
    static ItemSortOrder resolve(String rawSort, String rawDir) {
        String sort = PhpStrings.asciiLower(PhpStrings.trim(rawSort));
        String dir = PhpStrings.asciiLower(PhpStrings.trim(rawDir));
        boolean desc = "desc".equals(dir);
        boolean asc = "asc".equals(dir);
        return switch (sort) {
            case "id" -> desc ? ID_DESC : ID_ASC;
            case "title" -> desc ? TITLE_DESC : TITLE_ASC;
            case "unit" -> desc ? UNIT_DESC : UNIT_ASC;
            case "level" -> asc ? LEVEL_ASC : LEVEL_DESC;
            case "created" -> asc ? CREATED_ASC : CREATED_DESC;
            default -> DEFAULT;
        };
    }
}
