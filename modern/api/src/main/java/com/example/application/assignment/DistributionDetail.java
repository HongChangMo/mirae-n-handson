package com.example.application.assignment;

import com.example.domain.assignment.Distribution;
import java.time.LocalDateTime;

/** 배포 이력 한 건의 조회 결과. */
public record DistributionDetail(
    Integer id,
    Integer assignmentId,
    String assignmentTitle,
    Integer classId,
    LocalDateTime distributedAt,
    boolean redistributed) {

    static DistributionDetail from(Distribution d) {
        return new DistributionDetail(
            d.getId(),
            d.getAssignment().getId(),
            d.getAssignment().getTitle(),
            d.getClassRoom().getId(),
            d.getDistributedAt(),
            d.isRedistributed());
    }
}
