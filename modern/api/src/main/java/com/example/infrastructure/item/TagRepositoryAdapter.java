package com.example.infrastructure.item;

import com.example.domain.item.Tag;
import com.example.domain.item.TagRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link TagRepository} 구현 — Spring Data 저장소에 넘긴다. */
@Repository
public class TagRepositoryAdapter implements TagRepository {

    private final TagJpaRepository tagJpaRepository;

    public TagRepositoryAdapter(TagJpaRepository tagJpaRepository) {
        this.tagJpaRepository = tagJpaRepository;
    }

    @Override
    public Optional<Tag> findByName(String name) {
        return tagJpaRepository.findByName(name);
    }
}
