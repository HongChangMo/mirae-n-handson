package com.example.infrastructure.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.domain.assignment.Assignment;
import com.example.domain.assignment.ClassRoom;
import com.example.domain.assignment.Distribution;
import com.example.domain.item.ItemFixtures;
import com.example.domain.item.Unit;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 배포 저장소 구현 — Spring Data 저장소에 넘긴 조회 결과와 정렬을 H2(MariaDB 모드)에서 확인한다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DistributionRepositoryImpl.class)
class DistributionRepositoryImplTest {

    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DistributionRepositoryImpl repository;

    private Distribution early;
    private Distribution late;
    private Distribution otherClass;

    @BeforeEach
    void seed() {
        Unit unit = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Assignment assignment = entityManager.persist(
            new Assignment(10, "분수 덧셈 연습", unit, SEED_TIME.plusDays(14), "O"));
        ClassRoom classOne = entityManager.persist(new ClassRoom(1, "5학년 1반", "TCH-01"));
        ClassRoom classTwo = entityManager.persist(new ClassRoom(2, "5학년 2반", "TCH-02"));
        late = entityManager.persist(new Distribution(assignment, classOne, SEED_TIME.plusDays(2)));
        early = entityManager.persist(new Distribution(assignment, classOne, SEED_TIME));
        otherClass = entityManager.persist(new Distribution(assignment, classTwo, SEED_TIME.plusDays(1)));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findWithDetailsById: 과제 · 학급을 함께 읽어 온다")
    void findsDistributionWithDetails() {
        Optional<Distribution> found = repository.findWithDetailsById(early.getId());

        assertThat(found).isPresent();
        Distribution distribution = found.get();
        assertThat(Hibernate.isInitialized(distribution.getAssignment())).isTrue();
        assertThat(Hibernate.isInitialized(distribution.getClassRoom())).isTrue();
        assertThat(distribution.getClassRoom().getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("findWithDetailsById: 없는 배포면 빈 Optional")
    void returnsEmptyForUnknownId() {
        assertThat(repository.findWithDetailsById(9999)).isEmpty();
    }

    @Test
    @DisplayName("findByClassRoomIdOrderByDistributedAtAsc: 그 학급의 배포만, 배포 시각 오름차순")
    void listsDistributionsOfClassInTimeOrder() {
        assertThat(repository.findByClassRoomIdOrderByDistributedAtAsc(1))
            .extracting(Distribution::getId)
            .containsExactly(early.getId(), late.getId())
            .doesNotContain(otherClass.getId());
    }
}
