package com.example.infrastructure.assignment;

import com.example.domain.assignment.Submission;
import com.example.domain.assignment.SubmissionRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** {@link SubmissionRepository} 구현 — Spring Data 저장소에 넘긴다. */
@Repository
public class SubmissionRepositoryImpl implements SubmissionRepository {

    private final SubmissionJpaRepository submissionJpaRepository;

    public SubmissionRepositoryImpl(SubmissionJpaRepository submissionJpaRepository) {
        this.submissionJpaRepository = submissionJpaRepository;
    }

    @Override
    public List<Submission> findByDistributionIdOrderByStudentIdAsc(Integer distributionId) {
        return submissionJpaRepository.findByDistributionIdOrderByStudentIdAsc(distributionId);
    }
}
