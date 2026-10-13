package com.example.infrastructure.item;

import com.example.domain.item.Unit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitJpaRepository extends JpaRepository<Unit, Integer> {

    Optional<Unit> findByCode(String code);

    List<Unit> findAllByOrderByGradeAscCodeAsc();
}
