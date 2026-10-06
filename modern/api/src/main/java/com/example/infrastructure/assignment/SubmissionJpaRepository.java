package com.example.infrastructure.assignment;

import com.example.domain.assignment.Submission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionJpaRepository extends JpaRepository<Submission, Integer> {

    List<Submission> findByDistributionIdInOrderByDistributionIdAscStudentIdAsc(Collection<Integer> distributionIds);
}
