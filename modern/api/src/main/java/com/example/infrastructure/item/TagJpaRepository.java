package com.example.infrastructure.item;

import com.example.domain.item.Tag;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagJpaRepository extends JpaRepository<Tag, Integer> {

    Optional<Tag> findByName(String name);
}
