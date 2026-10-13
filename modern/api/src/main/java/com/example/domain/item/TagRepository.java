package com.example.domain.item;

import java.util.Optional;

/** 태그 저장소. 구현은 {@code infrastructure.item} 에 둔다. */
public interface TagRepository {

    Optional<Tag> findByName(String name);
}
