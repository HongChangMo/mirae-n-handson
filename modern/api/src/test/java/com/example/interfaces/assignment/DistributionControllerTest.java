package com.example.interfaces.assignment;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.application.assignment.DistributionDetail;
import com.example.application.assignment.DistributionService;
import com.example.common.NotFoundException;
import com.example.domain.assignment.AssignmentClosedException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 서비스 결과를 응답 DTO 로 옮길 때 JSON 필드명이 그대로인지 확인한다. */
@WebMvcTest(DistributionController.class)
@ActiveProfiles("test")
class DistributionControllerTest {

    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DistributionService distributionService;

    @Test
    @DisplayName("GET /api/distributions/{id} → 200, 배포 JSON")
    void getDistributionReturnsJson() throws Exception {
        when(distributionService.getDistribution(5)).thenReturn(
            new DistributionDetail(5, 10, "분수 덧셈 연습", 1, SEED_TIME, false));

        mockMvc.perform(get("/api/distributions/5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.assignmentId").value(10))
            .andExpect(jsonPath("$.assignmentTitle").value("분수 덧셈 연습"))
            .andExpect(jsonPath("$.classId").value(1))
            .andExpect(jsonPath("$.distributedAt").exists())
            .andExpect(jsonPath("$.redistributed").value(false));
    }

    @Test
    @DisplayName("GET /api/distributions/{id} 없는 배포 → 404")
    void getDistributionMissingReturns404() throws Exception {
        when(distributionService.getDistribution(99)).thenThrow(new NotFoundException("배포가 없습니다: id=99"));

        mockMvc.perform(get("/api/distributions/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/distributions/{id}/redistribute → 재배포 건과 같은 학급의 배포 이력")
    void redistributeReturnsDistributionAndHistory() throws Exception {
        DistributionDetail redistributed = new DistributionDetail(5, 10, "분수 덧셈 연습", 1, SEED_TIME, true);
        when(distributionService.redistribute(5, "출제 오류 수정")).thenReturn(redistributed);
        when(distributionService.listByClass(1)).thenReturn(List.of(
            new DistributionDetail(4, 11, "분수 뺄셈 연습", 1, SEED_TIME.minusDays(1), false),
            redistributed));

        mockMvc.perform(post("/api/distributions/5/redistribute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"출제 오류 수정\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.distribution.id").value(5))
            .andExpect(jsonPath("$.distribution.redistributed").value(true))
            .andExpect(jsonPath("$.history.length()").value(2))
            .andExpect(jsonPath("$.history[0].id").value(4))
            .andExpect(jsonPath("$.history[1].id").value(5));
    }

    @Test
    @DisplayName("POST /api/distributions/{id}/redistribute 마감된 과제 → 409, 공통 오류 응답")
    void redistributeClosedAssignmentReturns409() throws Exception {
        when(distributionService.redistribute(6, null))
            .thenThrow(new AssignmentClosedException("마감된 과제는 재배포할 수 없습니다: distributionId=6"));

        mockMvc.perform(post("/api/distributions/6/redistribute"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("마감된 과제는 재배포할 수 없습니다: distributionId=6"));
    }

    @Test
    @DisplayName("도메인 예외가 아닌 IllegalStateException → 409 가 아니라 500, 내부 메시지를 노출하지 않는다")
    void unexpectedIllegalStateReturns500() throws Exception {
        when(distributionService.getDistribution(7)).thenThrow(new IllegalStateException("내부 상태 오류"));

        mockMvc.perform(get("/api/distributions/7"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("서버 내부 오류"));
    }
}
