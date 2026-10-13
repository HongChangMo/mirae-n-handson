package com.example.domain.assignment;

import java.util.Optional;

/** 학급 저장소. 구현은 {@code infrastructure.assignment} 에 둔다. */
public interface ClassRoomRepository {

    Optional<ClassRoom> findById(Integer id);
}
