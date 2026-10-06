package com.example.domain.assignment;

import com.example.common.ConflictException;

/** 마감된 과제에 허용되지 않는 작업(재배포 등)을 요청했을 때. */
public class AssignmentClosedException extends ConflictException {

    public AssignmentClosedException(String message) {
        super(message);
    }
}
