package com.example.interfaces.item;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.application.item.UnitService;
import com.example.application.item.UnitSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 단원 조회 결과를 응답 DTO 로 옮길 때 JSON 필드명 · 순서가 그대로인지 확인한다. */
@WebMvcTest(UnitController.class)
@ActiveProfiles("test")
class UnitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnitService unitService;

    @Test
    @DisplayName("GET /api/units → 200, 서비스가 준 순서 그대로 id · code · name · grade 배열")
    void listUnitsReturnsArray() throws Exception {
        when(unitService.listUnits()).thenReturn(List.of(
            new UnitSummary(1, "M5-1", "분수의 덧셈과 뺄셈", 5),
            new UnitSummary(5, "M6-2", "비와 비율", 6)));

        mockMvc.perform(get("/api/units"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].code").value("M5-1"))
            .andExpect(jsonPath("$[0].name").value("분수의 덧셈과 뺄셈"))
            .andExpect(jsonPath("$[0].grade").value(5))
            .andExpect(jsonPath("$[1].code").value("M6-2"));
    }

    @Test
    @DisplayName("GET /api/units 단원이 없으면 → 200, 빈 배열")
    void listUnitsReturnsEmptyArray() throws Exception {
        when(unitService.listUnits()).thenReturn(List.of());

        mockMvc.perform(get("/api/units"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
