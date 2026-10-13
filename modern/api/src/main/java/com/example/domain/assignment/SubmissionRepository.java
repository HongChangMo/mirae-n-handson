package com.example.domain.assignment;

import java.util.Collection;
import java.util.List;

/** 학생 제출 저장소. 구현은 {@code infrastructure.assignment} 에 둔다. */
public interface SubmissionRepository {

    /** 여러 배포의 제출 목록을 쿼리 한 번으로 — 배포 id 오름차순, 같은 배포 안에서는 학생 ID 오름차순. */
    List<Submission> findByDistributionIds(Collection<Integer> distributionIds);
}
