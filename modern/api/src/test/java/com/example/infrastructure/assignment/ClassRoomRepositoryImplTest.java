package com.example.infrastructure.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.domain.assignment.ClassRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 학급 저장소 구현 — Spring Data 저장소에 넘긴 결과를 H2(MariaDB 모드)에서 확인한다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ClassRoomRepositoryImpl.class)
class ClassRoomRepositoryImplTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ClassRoomRepositoryImpl repository;

    @BeforeEach
    void seed() {
        entityManager.persist(new ClassRoom(1, "5학년 1반", "TCH-01"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findById: 있는 학급을 돌려준다")
    void findsClassRoomById() {
        assertThat(repository.findById(1)).get().extracting(ClassRoom::getName).isEqualTo("5학년 1반");
    }

    @Test
    @DisplayName("findById: 없는 학급이면 빈 Optional")
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById(99)).isEmpty();
    }
}
