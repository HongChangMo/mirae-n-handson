package com.example.infrastructure.assignment;

import com.example.domain.assignment.Distribution;
import com.example.domain.assignment.DistributionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link DistributionRepository} 구현 — Spring Data 저장소에 넘긴다. */
@Repository
public class DistributionRepositoryImpl implements DistributionRepository {

    private final DistributionJpaRepository distributionJpaRepository;

    public DistributionRepositoryImpl(DistributionJpaRepository distributionJpaRepository) {
        this.distributionJpaRepository = distributionJpaRepository;
    }

    @Override
    public Optional<Distribution> findWithDetailsById(Integer id) {
        return distributionJpaRepository.findWithDetailsById(id);
    }

    @Override
    public List<Distribution> findByClassRoomIdOrderByDistributedAtAsc(Integer classRoomId) {
        return distributionJpaRepository.findByClassRoomIdOrderByDistributedAtAsc(classRoomId);
    }
}
