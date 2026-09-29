package com.example.infrastructure.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.domain.item.Item;
import com.example.domain.item.ItemFixtures;
import com.example.domain.item.ItemSearchCondition;
import com.example.domain.item.ItemSearchPage;
import com.example.domain.item.ItemStatus;
import com.example.domain.item.ItemSummary;
import com.example.domain.item.Tag;
import com.example.domain.item.Unit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 저장소 어댑터 — 검색 JPQL 을 H2(MariaDB 모드)에 실제로 날린다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ItemRepositoryAdapter.class)
class ItemRepositoryAdapterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemRepositoryAdapter adapter;

    private Unit fraction;
    private Unit ratio;
    private Tag calc;
    private Tag word;
    private Tag concept;

    @BeforeEach
    void seed() {
        fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        ratio = entityManager.persist(ItemFixtures.unit(5, "M6-2", "비와 비율", 6));
        calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        word = entityManager.persist(ItemFixtures.tag(2, "문장제"));
        concept = entityManager.persist(ItemFixtures.tag(3, "개념"));
    }

    private Integer persist(Unit unit, String title, int level, String status, Tag... tags) {
        Item item = entityManager.persist(ItemFixtures.item(null, unit, title, level, status, tags));
        return item.getId();
    }

    private ItemSearchPage search(String q, String unit, String level, String tag, String sort, String dir,
        String page) {
        entityManager.flush();
        entityManager.clear();
        return adapter.search(ItemSearchCondition.of(q, unit, level, tag, sort, dir, page));
    }

    private static List<Integer> ids(ItemSearchPage page) {
        return page.rows().stream().map(ItemSummary::id).toList();
    }

    @Test
    @DisplayName("search 기본: 공개(A)만 · 난이도 5 제외 · level DESC, id ASC (BR-01 BR-10 BR-17)")
    void defaultSearchExcludesHiddenAndLevelFive() {
        Integer easy = persist(fraction, "쉬운 문항", 1, ItemStatus.ACTIVE);
        Integer hardA = persist(ratio, "어려운 문항 A", 4, ItemStatus.ACTIVE);
        Integer hardB = persist(fraction, "어려운 문항 B", 4, ItemStatus.ACTIVE);
        persist(fraction, "최상 문항", 5, ItemStatus.ACTIVE);
        persist(fraction, "검수 중", 2, ItemStatus.REVIEWING);
        persist(fraction, "삭제됨", 2, ItemStatus.DELETED);

        ItemSearchPage page = search(null, null, null, null, null, null, null);

        assertThat(page.total()).isEqualTo(3);
        assertThat(ids(page)).containsExactly(hardA, hardB, easy);
    }

    @Test
    @DisplayName("search level=5: 난이도 5 는 명시했을 때만 나온다 (BR-11)")
    void explicitLevelFiveIsFound() {
        persist(fraction, "쉬운 문항", 1, ItemStatus.ACTIVE);
        Integer top = persist(fraction, "최상 문항", 5, ItemStatus.ACTIVE);

        assertThat(ids(search(null, null, "5", null, null, null, null))).containsExactly(top);
    }

    @Test
    @DisplayName("search: 범위 밖 난이도 · int 범위를 넘는 난이도는 0건, 오류 아님 (BR-12)")
    void outOfRangeLevelGivesNoRows() {
        persist(fraction, "쉬운 문항", 1, ItemStatus.ACTIVE);

        assertThat(search(null, null, "6", null, null, null, null).total()).isZero();
        assertThat(search(null, null, "99999999999999999999", null, null, null, null).total()).isZero();
    }

    @Test
    @DisplayName("search: 키워드는 제목 또는 지문, 단원 · 난이도 · 태그와 AND (BR-02 BR-04 BR-08 BR-13)")
    void combinesConditionsWithAnd() {
        Integer match = persist(fraction, "분수의 덧셈", 1, ItemStatus.ACTIVE, calc);
        persist(fraction, "분수의 뺄셈", 1, ItemStatus.ACTIVE, word);
        persist(fraction, "분수의 곱셈", 2, ItemStatus.ACTIVE, calc);
        persist(ratio, "분수와 비", 1, ItemStatus.ACTIVE, calc);
        Integer byStem = persist(fraction, "덧셈 연습", 1, ItemStatus.ACTIVE, calc);

        ItemSearchPage page = search("분수", "M5-1", "1", "계산", null, null, null);

        // ItemFixtures 의 지문은 "<제목> 문제 본문" 이라 "덧셈 연습" 은 지문에도 "분수" 가 없다
        assertThat(ids(page)).containsExactly(match);
        assertThat(ids(search("연습 문제", null, null, null, null, null, null))).containsExactly(byStem);
    }

    @Test
    @DisplayName("search: 키워드의 % · _ 는 와일드카드로 동작한다 (BR-06)")
    void keywordWildcardsAreNotEscaped() {
        Integer id = persist(fraction, "분수의 덧셈", 1, ItemStatus.ACTIVE);

        assertThat(ids(search("분%덧", null, null, null, null, null, null))).containsExactly(id);
        assertThat(ids(search("분수의_덧셈", null, null, null, null, null, null))).containsExactly(id);
    }

    @Test
    @DisplayName("search: 태그는 정확 일치 — % 는 와일드카드가 아니다 (BR-13 BR-15)")
    void tagIsExactMatch() {
        persist(fraction, "계산 문항", 1, ItemStatus.ACTIVE, calc);

        assertThat(search(null, null, null, "계%", null, null, null).total()).isZero();
        assertThat(search(null, null, null, "계산", null, null, null).total()).isEqualTo(1);
    }

    @Test
    @DisplayName("search: 없는 단원 · 미등록 태그는 0건 (BR-09 BR-16)")
    void unknownUnitOrTagGivesNoRows() {
        persist(fraction, "쉬운 문항", 1, ItemStatus.ACTIVE, calc);

        assertThat(search(null, "Z99-99", null, null, null, null, null).rows()).isEmpty();
        assertThat(search(null, null, null, "없는태그", null, null, null).total()).isZero();
    }

    @Test
    @DisplayName("search: 행에는 단원 코드와 태그 이름(태그 id 순)이 담긴다 (BR-30)")
    void rowsCarryUnitCodeAndTagsInIdOrder() {
        Integer id = persist(ratio, "태그 많은 문항", 3, ItemStatus.ACTIVE, concept, calc, word);
        Integer noTag = persist(ratio, "태그 없는 문항", 3, ItemStatus.ACTIVE);

        List<ItemSummary> rows = search(null, null, null, null, null, null, null).rows();

        assertThat(rows).containsExactly(
            new ItemSummary(id, "태그 많은 문항", "M6-2", 3, List.of("계산", "문장제", "개념")),
            new ItemSummary(noTag, "태그 없는 문항", "M6-2", 3, List.of()));
    }

    @Test
    @DisplayName("search sort=unit dir=desc: 단원 코드 DESC → level DESC → id ASC (BR-22 BR-23)")
    void sortsByUnitThenLevelThenId() {
        Integer f1 = persist(fraction, "분수 1", 1, ItemStatus.ACTIVE);
        Integer f3 = persist(fraction, "분수 3", 3, ItemStatus.ACTIVE);
        Integer r2a = persist(ratio, "비 2a", 2, ItemStatus.ACTIVE);
        Integer r2b = persist(ratio, "비 2b", 2, ItemStatus.ACTIVE);

        assertThat(ids(search(null, null, null, null, "unit", "desc", null))).containsExactly(r2a, r2b, f3, f1);
        assertThat(ids(search(null, null, null, null, "unit", null, null))).containsExactly(f3, f1, r2a, r2b);
    }

    @Test
    @DisplayName("search: 20건씩 페이지, 범위 밖 페이지는 건수만 있고 행 없음 (BR-25 BR-27 BR-28)")
    void pagesByTwenty() {
        for (int n = 1; n <= 22; n++) {
            persist(fraction, "문항 " + n, 1, ItemStatus.ACTIVE);
        }

        ItemSearchPage first = search(null, null, null, null, "id", null, null);
        ItemSearchPage second = search(null, null, null, null, "id", null, "2");
        ItemSearchPage beyond = search(null, null, null, null, "id", null, "1000");

        assertThat(first.total()).isEqualTo(22);
        assertThat(first.rows()).hasSize(ItemSearchCondition.PAGE_SIZE);
        assertThat(second.rows()).extracting(ItemSummary::title).containsExactly("문항 21", "문항 22");
        assertThat(beyond.total()).isEqualTo(22);
        assertThat(beyond.rows()).isEmpty();
    }

    @Test
    @DisplayName("기존 조회 메서드는 Spring Data 저장소에 그대로 넘긴다")
    void delegatesExistingQueries() {
        Integer id = persist(fraction, "위임 확인", 2, ItemStatus.ACTIVE, calc);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.findWithDetailsById(id)).get().extracting(Item::getTitle).isEqualTo("위임 확인");
        assertThat(adapter.findByUnitCodeAndStatus("M5-1", ItemStatus.ACTIVE)).extracting(Item::getId)
            .containsExactly(id);
        assertThat(adapter.countByUnitIdAndStatus(fraction.getId(), ItemStatus.ACTIVE)).isEqualTo(1);
    }
}
