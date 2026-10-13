package com.example.domain.assignment;

import java.util.List;
import java.util.Optional;

/** 과제 배포 저장소. 구현은 {@code infrastructure.assignment} 에 둔다. */
public interface DistributionRepository {

    /** 단건 조회 — 과제 · 학급을 함께 가져온다(OSIV 꺼져 있음). */
    Optional<Distribution> findWithDetailsById(Integer id);

    /** 학급의 배포 이력(배포 시각 오름차순). 과제 · 학급을 함께 가져온다. */
    List<Distribution> findByClassRoomIdOrderByDistributedAtAsc(Integer classRoomId);
}
