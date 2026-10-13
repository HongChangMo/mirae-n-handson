package com.example.infrastructure.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.domain.item.ItemFixtures;
import com.example.domain.item.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 단원 저장소 구현 — Spring Data 저장소에 넘긴 결과를 H2(MariaDB 모드)에서 확인한다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(UnitRepositoryImpl.class)
class UnitRepositoryImplTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UnitRepositoryImpl repository;

    @BeforeEach
    void seed() {
        entityManager.persist(ItemFixtures.unit(5, "M6-2", "비와 비율", 6));
        entityManager.persist(ItemFixtures.unit(2, "M5-2", "약분과 통분", 5));
        entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findByCode: 코드가 일치하는 단원을 돌려준다")
    void findsUnitByCode() {
        assertThat(repository.findByCode("M5-2")).get().extracting(Unit::getId).isEqualTo(2);
    }

    @Test
    @DisplayName("findByCode: 없는 코드면 빈 Optional")
    void returnsEmptyForUnknownCode() {
        assertThat(repository.findByCode("Z99-99")).isEmpty();
    }

    @Test
    @DisplayName("findAllByOrderByGradeAscCodeAsc: 학년 오름차순, 같은 학년이면 코드 오름차순")
    void listsUnitsByGradeThenCode() {
        assertThat(repository.findAllByOrderByGradeAscCodeAsc())
            .extracting(Unit::getCode)
            .containsExactly("M5-1", "M5-2", "M6-2");
    }
}
