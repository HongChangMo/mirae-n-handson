package com.example.infrastructure.item;

import com.example.domain.item.Unit;
import com.example.domain.item.UnitRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link UnitRepository} 구현 — Spring Data 저장소에 넘긴다. */
@Repository
public class UnitRepositoryImpl implements UnitRepository {

    private final UnitJpaRepository unitJpaRepository;

    public UnitRepositoryImpl(UnitJpaRepository unitJpaRepository) {
        this.unitJpaRepository = unitJpaRepository;
    }

    @Override
    public Optional<Unit> findByCode(String code) {
        return unitJpaRepository.findByCode(code);
    }

    @Override
    public List<Unit> findAllByOrderByGradeAscCodeAsc() {
        return unitJpaRepository.findAllByOrderByGradeAscCodeAsc();
    }
}
