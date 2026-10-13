package com.example.common;

/** 요청이 리소스의 현재 상태와 맞지 않을 때 던지는 예외의 상위 타입. {@link GlobalExceptionHandler}가 409로 변환한다. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
