package com.example.infrastructure.assignment;

import com.example.domain.assignment.ClassRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRoomJpaRepository extends JpaRepository<ClassRoom, Integer> {
}
