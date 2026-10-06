package com.example.interfaces.assignment;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.application.assignment.DistributionDetail;
import com.example.application.assignment.DistributionService;
import com.example.common.NotFoundException;
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
}
