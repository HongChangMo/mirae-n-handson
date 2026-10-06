package com.example.infrastructure.assignment;

import com.example.domain.assignment.ClassRoom;
import com.example.domain.assignment.ClassRoomRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link ClassRoomRepository} 구현 — Spring Data 저장소에 넘긴다. */
@Repository
public class ClassRoomRepositoryImpl implements ClassRoomRepository {

    private final ClassRoomJpaRepository classRoomJpaRepository;

    public ClassRoomRepositoryImpl(ClassRoomJpaRepository classRoomJpaRepository) {
        this.classRoomJpaRepository = classRoomJpaRepository;
    }

    @Override
    public Optional<ClassRoom> findById(Integer id) {
        return classRoomJpaRepository.findById(id);
    }
}
