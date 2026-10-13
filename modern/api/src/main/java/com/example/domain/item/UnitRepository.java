package com.example.domain.item;

import java.util.List;
import java.util.Optional;

/** 단원 저장소. 구현은 {@code infrastructure.item} 에 둔다. */
public interface UnitRepository {

    Optional<Unit> findByCode(String code);

    List<Unit> findAllByOrderByGradeAscCodeAsc();
}
