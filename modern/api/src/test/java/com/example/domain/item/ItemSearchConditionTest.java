package com.example.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ItemSearchConditionTest {

    private static ItemSearchCondition level(String level) {
        return ItemSearchCondition.of(null, null, level, null, null, null, null);
    }

    private static ItemSearchCondition sort(String sort, String dir) {
        return ItemSearchCondition.of(null, null, null, null, sort, dir, null);
    }

    private static ItemSearchCondition page(String page) {
        return ItemSearchCondition.of(null, null, null, null, null, null, page);
    }

    @Test
    @DisplayName("파라미터가 없으면 조건 없음 · 난이도 5 미만 · 기본 정렬 · 1페이지 (BR-10 BR-17 BR-26)")
    void absentParamsGiveDefaultCondition() {
        ItemSearchCondition condition = ItemSearchCondition.of(null, null, null, null, null, null, null);

        assertThat(condition.keyword()).isNull();
        assertThat(condition.unitCode()).isNull();
        assertThat(condition.tag()).isNull();
        assertThat(condition.level()).isEqualTo(LevelFilter.belowMax(ItemSearchCondition.DEFAULT_LEVEL_EXCLUSIVE_MAX));
        assertThat(condition.sortOrder()).isEqualTo(ItemSortOrder.DEFAULT);
        assertThat(condition.page()).isEqualTo(1);
        assertThat(condition.offset()).isZero();
    }

    @Test
    @DisplayName("빈 문자열 · 공백만 있는 값은 조건 없음과 같다 (BR-04 BR-08 BR-10 BR-13)")
    void blankValuesAreTreatedAsAbsent() {
        ItemSearchCondition condition = ItemSearchCondition.of("  ", " ", " ", "\t", "", "", "");

        assertThat(condition)
            .isEqualTo(ItemSearchCondition.of(null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("키워드는 trim 후 100자(문자 수)까지만 쓴다 (BR-04 BR-05)")
    void keywordIsTrimmedAndTruncated() {
        assertThat(ItemSearchCondition.of(" 분수 ", null, null, null, null, null, null).keyword())
            .isEqualTo("분수");
        assertThat(ItemSearchCondition.of("가".repeat(100) + "나", null, null, null, null, null, null).keyword())
            .isEqualTo("가".repeat(100));
        assertThat(ItemSearchCondition.of("a".repeat(1000), null, null, null, null, null, null).keyword())
            .hasSize(ItemSearchCondition.KEYWORD_MAX_LENGTH);
    }

    @Test
    @DisplayName("키워드의 % · _ 는 그대로 둔다 — 와일드카드로 동작 (BR-06)")
    void keywordKeepsWildcards() {
        assertThat(ItemSearchCondition.of("분%_수", null, null, null, null, null, null).keyword())
            .isEqualTo("분%_수");
    }

    @Test
    @DisplayName("단원 코드는 trim 만 하고 형식 · 대소문자가 틀려도 그대로 쓴다 (BR-08 BR-09)")
    void unitCodeIsKeptAsIs() {
        assertThat(ItemSearchCondition.of(null, " M5-1 ", null, null, null, null, null).unitCode()).isEqualTo("M5-1");
        assertThat(ItemSearchCondition.of(null, "z99-99", null, null, null, null, null).unitCode()).isEqualTo("z99-99");
        assertThat(ItemSearchCondition.of(null, "아무거나", null, null, null, null, null).unitCode()).isEqualTo("아무거나");
    }

    @ParameterizedTest(name = "level={0} → 난이도 {1}")
    @CsvSource({"1, 1", "5, 5", "' 3 ', 3", "6, 6", "0, 0", "3a, 3", "3.7, 3", "abc, 0", "-2, -2", "1e3, 1000"})
    @DisplayName("난이도가 있으면 (int) 변환 값과 같은 문항 (BR-11 BR-12)")
    void levelIsComparedAsPhpInt(String raw, long expected) {
        assertThat(level(raw).level()).isEqualTo(LevelFilter.equalTo(expected));
    }

    @Test
    @DisplayName("태그는 trim 후 50자까지만 쓴다 (BR-13 BR-14)")
    void tagIsTrimmedAndTruncated() {
        String tag = ItemSearchCondition.of(null, null, null, "계산" + "x".repeat(49), null, null, null).tag();

        assertThat(tag).isEqualTo("계산" + "x".repeat(48));
        assertThat(tag).hasSize(ItemSearchCondition.TAG_MAX_LENGTH);
        assertThat(ItemSearchCondition.of(null, null, null, " 계산 ", null, null, null).tag()).isEqualTo("계산");
    }

    @ParameterizedTest(name = "sort={0}, dir={1} → {2}")
    @CsvSource(nullValues = "NULL", value = {
        "NULL, NULL, DEFAULT",
        "'', desc, DEFAULT",
        "id, NULL, ID_ASC",
        "id, desc, ID_DESC",
        "title, UP, TITLE_ASC",
        "title, ' DESC ', TITLE_DESC",
        "unit, asc, UNIT_ASC",
        "unit, desc, UNIT_DESC",
        "' LEVEL ', UP, LEVEL_DESC",
        "level, asc, LEVEL_ASC",
        "created, NULL, CREATED_DESC",
        "created, ASC, CREATED_ASC",
        "bogus, asc, DEFAULT"
    })
    @DisplayName("정렬 기준 · 방향 해석 (BR-17 ~ BR-21)")
    void resolvesSortOrder(String sort, String dir, ItemSortOrder expected) {
        assertThat(sort(sort, dir).sortOrder()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "page={0} → {1}")
    @CsvSource(nullValues = "NULL", value = {
        "NULL, 1", "'', 1", "1, 1", "2, 2", "0, 1", "-1, 1", "abc, 1", "' 2', 1",
        "999, 999", "1000, 999", "99999999999999999999, 999"
    })
    @DisplayName("페이지 번호: 숫자만 인정, 1 미만은 1, 999 초과는 999 (BR-26 BR-27)")
    void resolvesPage(String raw, int expected) {
        assertThat(page(raw).page()).isEqualTo(expected);
    }

    @Test
    @DisplayName("페이지 번호 끝의 줄바꿈 하나는 허용한다 — preg_match 의 $ (BR-26)")
    void pageAllowsTrailingNewline() {
        assertThat(page("2\n").page()).isEqualTo(2);
    }

    @Test
    @DisplayName("offset = (page - 1) × 20 (BR-25)")
    void offsetUsesPageSize() {
        assertThat(page("3").offset()).isEqualTo(2 * ItemSearchCondition.PAGE_SIZE);
        assertThat(page("1000").offset()).isEqualTo(998 * 20);
    }

    @Test
    @DisplayName("정렬 문자열: 보조 키 id ASC 고정, 단원 정렬의 2차 키 level DESC 고정 (BR-22 BR-23)")
    void sortOrderJpqlKeepsTieBreakers() {
        assertThat(ItemSortOrder.DEFAULT.jpql()).isEqualTo("i.level desc, i.id asc");
        assertThat(ItemSortOrder.ID_DESC.jpql()).isEqualTo("i.id desc");
        assertThat(ItemSortOrder.TITLE_DESC.jpql()).isEqualTo("i.title desc, i.id asc");
        assertThat(ItemSortOrder.UNIT_ASC.jpql()).isEqualTo("u.code asc, i.level desc, i.id asc");
        assertThat(ItemSortOrder.UNIT_DESC.jpql()).isEqualTo("u.code desc, i.level desc, i.id asc");
        assertThat(ItemSortOrder.CREATED_DESC.jpql()).isEqualTo("i.createdAt desc, i.id asc");
    }
}
