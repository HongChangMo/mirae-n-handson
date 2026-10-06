package com.example.infrastructure.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.domain.assignment.Assignment;
import com.example.domain.assignment.ClassRoom;
import com.example.domain.assignment.Distribution;
import com.example.domain.assignment.Submission;
import com.example.domain.item.ItemFixtures;
import com.example.domain.item.Unit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

/** 제출 저장소 — 여러 배포의 제출을 쿼리 한 번으로 읽는 정렬 규칙을 H2(MariaDB 모드)에서 확인한다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(SubmissionRepositoryImpl.class)
class SubmissionRepositoryImplTest {

    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubmissionRepositoryImpl repository;

    private Distribution first;
    private Distribution second;
    private Distribution other;

    @BeforeEach
    void seed() {
        Unit unit = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Assignment assignment = entityManager.persist(
            new Assignment(10, "분수 덧셈 연습", unit, SEED_TIME.plusDays(14), "O"));
        ClassRoom classRoom = entityManager.persist(new ClassRoom(1, "5학년 1반", "TCH-01"));
        first = entityManager.persist(new Distribution(assignment, classRoom, SEED_TIME));
        second = entityManager.persist(new Distribution(assignment, classRoom, SEED_TIME.plusDays(1)));
        other = entityManager.persist(new Distribution(assignment, classRoom, SEED_TIME.plusDays(2)));

        entityManager.persist(new Submission(second, "STU-1002", SEED_TIME, new BigDecimal("70.0")));
        entityManager.persist(new Submission(first, "STU-1003", SEED_TIME, null));
        entityManager.persist(new Submission(second, "STU-1001", SEED_TIME, new BigDecimal("90.0")));
        entityManager.persist(new Submission(first, "STU-1001", SEED_TIME, new BigDecimal("80.0")));
        entityManager.persist(new Submission(other, "STU-1004", SEED_TIME, null));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findByDistributionIds: 요청한 배포의 제출만, 배포 id · 학생 ID 오름차순")
    void findsSubmissionsOfGivenDistributionsInOrder() {
        List<Submission> submissions = repository.findByDistributionIds(List.of(second.getId(), first.getId()));

        assertThat(submissions)
            .extracting(s -> s.getDistribution().getId(), Submission::getStudentId)
            .containsExactly(
                tuple(first.getId(), "STU-1001"),
                tuple(first.getId(), "STU-1003"),
                tuple(second.getId(), "STU-1001"),
                tuple(second.getId(), "STU-1002"));
    }

    @Test
    @DisplayName("findByDistributionIds: 빈 목록이면 쿼리 없이 빈 결과")
    void returnsEmptyForNoDistributions() {
        assertThat(repository.findByDistributionIds(List.of())).isEmpty();
    }
}
