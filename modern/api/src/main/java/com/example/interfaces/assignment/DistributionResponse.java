package com.example.interfaces.assignment;

import com.example.application.assignment.DistributionDetail;
import java.time.LocalDateTime;

/** 배포 이력 한 건. */
public record DistributionResponse(
    Integer id,
    Integer assignmentId,
    String assignmentTitle,
    Integer classId,
    LocalDateTime distributedAt,
    boolean redistributed) {

    static DistributionResponse from(DistributionDetail d) {
        return new DistributionResponse(
            d.id(),
            d.assignmentId(),
            d.assignmentTitle(),
            d.classId(),
            d.distributedAt(),
            d.redistributed());
    }
}
