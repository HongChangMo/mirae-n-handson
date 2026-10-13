package com.example.interfaces.assignment;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.application.assignment.ClassReport;
import com.example.application.assignment.ReportService;
import com.example.common.NotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 서비스 결과를 응답 DTO 로 옮길 때 JSON 필드명이 그대로인지 확인한다. */
@WebMvcTest(ReportController.class)
@ActiveProfiles("test")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @Test
    @DisplayName("GET /api/classes/{id}/report → 200, 학급 리포트 JSON")
    void classReportReturnsJson() throws Exception {
        when(reportService.buildClassReport(1)).thenReturn(
            new ClassReport(1, "5학년 1반", 2, 3, new BigDecimal("87.8"), "abc123"));

        mockMvc.perform(get("/api/classes/1/report"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.classId").value(1))
            .andExpect(jsonPath("$.className").value("5학년 1반"))
            .andExpect(jsonPath("$.assignmentCount").value(2))
            .andExpect(jsonPath("$.submissionCount").value(3))
            .andExpect(jsonPath("$.averageScore").value(87.8))
            .andExpect(jsonPath("$.signature").value("abc123"));
    }

    @Test
    @DisplayName("GET /api/classes/{id}/report 없는 학급 → 404")
    void classReportMissingReturns404() throws Exception {
        when(reportService.buildClassReport(9)).thenThrow(new NotFoundException("학급이 없습니다: id=9"));

        mockMvc.perform(get("/api/classes/9/report"))
            .andExpect(status().isNotFound());
    }
}
