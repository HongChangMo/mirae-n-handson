package com.example.infrastructure.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.domain.item.ItemFixtures;
import com.example.domain.item.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 태그 저장소 구현 — Spring Data 저장소에 넘긴 결과를 H2(MariaDB 모드)에서 확인한다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TagRepositoryImpl.class)
class TagRepositoryImplTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TagRepositoryImpl repository;

    @BeforeEach
    void seed() {
        entityManager.persist(ItemFixtures.tag(1, "계산"));
        entityManager.persist(ItemFixtures.tag(2, "문장제"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findByName: 이름이 정확히 일치하는 태그를 돌려준다")
    void findsTagByName() {
        assertThat(repository.findByName("문장제")).get().extracting(Tag::getId).isEqualTo(2);
    }

    @Test
    @DisplayName("findByName: 미등록 태그 · 부분 일치는 빈 Optional")
    void returnsEmptyForUnknownName() {
        assertThat(repository.findByName("없는태그")).isEmpty();
        assertThat(repository.findByName("계")).isEmpty();
    }
}
