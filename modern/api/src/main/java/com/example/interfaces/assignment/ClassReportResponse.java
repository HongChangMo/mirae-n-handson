package com.example.interfaces.assignment;

import com.example.application.assignment.ClassReport;
import java.math.BigDecimal;

/** 학급 리포트 응답 — 과제 수 · 제출 수 · 평균 점수 · 리포트 서명(외부 집계 시스템 형식). */
public record ClassReportResponse(
    Integer classId,
    String className,
    int assignmentCount,
    int submissionCount,
    BigDecimal averageScore,
    String signature) {

    static ClassReportResponse from(ClassReport report) {
        return new ClassReportResponse(
            report.classId(),
            report.className(),
            report.assignmentCount(),
            report.submissionCount(),
            report.averageScore(),
            report.signature());
    }
}
