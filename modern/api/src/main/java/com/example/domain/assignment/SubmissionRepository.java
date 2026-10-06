package com.example.domain.assignment;

import java.util.List;

/** 학생 제출 저장소. 구현은 {@code infrastructure.assignment} 에 둔다. */
public interface SubmissionRepository {

    /** 배포 한 건의 제출 목록(학생 ID 오름차순). */
    List<Submission> findByDistributionIdOrderByStudentIdAsc(Integer distributionId);
}
