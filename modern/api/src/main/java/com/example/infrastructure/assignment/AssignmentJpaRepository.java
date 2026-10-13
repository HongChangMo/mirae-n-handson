package com.example.infrastructure.assignment;

import com.example.domain.assignment.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

/** 과제 Spring Data 저장소. 아직 쓰는 곳이 없어 도메인 인터페이스는 쓰임이 생길 때 만든다. */
public interface AssignmentJpaRepository extends JpaRepository<Assignment, Integer> {
}
