package com.example.interfaces.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.application.item.ItemSearchCommand;
import com.example.application.item.ItemSearchResult;
import com.example.application.item.ItemSearchService;
import com.example.application.item.ItemService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 문항 검색 컨트롤러 슬라이스 — 응답 모양은 characterization 정규화 결과 {rows, count, message} 와 같다. */
@WebMvcTest({ItemSearchController.class, ItemController.class})
@ActiveProfiles("test")
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemSearchService itemSearchService;

    @MockBean
    private ItemService itemService;

    @BeforeEach
    void emptyResultByDefault() {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResult(0, List.of()));
    }

    private ItemSearchCommand capturedCommand() {
        ArgumentCaptor<ItemSearchCommand> captor = ArgumentCaptor.forClass(ItemSearchCommand.class);
        verify(itemSearchService).search(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("GET /api/items/search → 200, rows 의 필드는 id · title · unit · level · tags 만, message 는 null")
    void searchReturnsRowsCountMessage() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResult(20, List.of(
            new ItemSearchResult.Row(18, "분수의 뺄셈 문장제", "M5-1", 4, List.of("문장제", "오답률높음")),
            new ItemSearchResult.Row(19, "세 분수의 곱셈", "M5-2", 4, List.of()))));

        mockMvc.perform(get("/api/items/search"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(20))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.rows[0].id").value(18))
            .andExpect(jsonPath("$.rows[0].title").value("분수의 뺄셈 문장제"))
            .andExpect(jsonPath("$.rows[0].unit").value("M5-1"))
            .andExpect(jsonPath("$.rows[0].level").value(4))
            .andExpect(jsonPath("$.rows[0].tags[1]").value("오답률높음"))
            .andExpect(jsonPath("$.rows[0].stem").doesNotExist())
            .andExpect(jsonPath("$.rows[0].status").doesNotExist())
            .andExpect(jsonPath("$.rows[1].tags.length()").value(0));
    }

    @Test
    @DisplayName("/api/items/search 는 /api/items/{id} 보다 먼저 잡힌다 — 400(숫자 아닌 id)이 아니다")
    void searchPathWinsOverItemId() throws Exception {
        mockMvc.perform(get("/api/items/search")).andExpect(status().isOk());

        verifyNoInteractions(itemService);
    }

    @Test
    @DisplayName("GET /api/items/search 0건 → 404 가 아니라 200, message '검색 결과가 없습니다'")
    void emptyResultHasNoResultMessage() throws Exception {
        mockMvc.perform(get("/api/items/search").param("unit", "Z99-99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.rows.length()").value(0))
            .andExpect(jsonPath("$.message").value(ItemSearchResponse.NO_RESULT_MESSAGE));
    }

    @Test
    @DisplayName("GET /api/items/search 건수는 있고 행이 없는 페이지 → message 는 null")
    void pageBeyondLastHasNoMessage() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResult(20, List.of()));

        mockMvc.perform(get("/api/items/search").param("page", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(20))
            .andExpect(jsonPath("$.rows.length()").value(0))
            .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    @DisplayName("파라미터 이름은 레거시와 같다: q · unit · level · tag · sort · dir · page")
    void passesLegacyParameterNames() throws Exception {
        mockMvc.perform(get("/api/items/search")
                .param("q", "분수").param("unit", "M5-1").param("level", "1").param("tag", "계산")
                .param("sort", "unit").param("dir", "desc").param("page", "2"))
            .andExpect(status().isOk());

        assertThat(capturedCommand())
            .isEqualTo(new ItemSearchCommand("분수", "M5-1", "1", "계산", "unit", "desc", "2"));
    }

    @Test
    @DisplayName("없는 파라미터는 null, 빈 파라미터는 빈 문자열로 넘긴다")
    void distinguishesAbsentAndEmpty() throws Exception {
        mockMvc.perform(get("/api/items/search").param("level", "").param("page", ""))
            .andExpect(status().isOk());

        assertThat(capturedCommand())
            .isEqualTo(new ItemSearchCommand(null, null, "", null, null, null, ""));
    }

    @Test
    @DisplayName("같은 이름이 여러 번 오면 마지막 값 — PHP $_GET 과 같다")
    void repeatedParameterUsesLastValue() throws Exception {
        mockMvc.perform(get("/api/items/search?q=a&q=b")).andExpect(status().isOk());

        assertThat(capturedCommand().q()).isEqualTo("b");
    }

    @Test
    @DisplayName("배열 파라미터(tag[]=…)는 첫 값만 쓴다 (BR-03)")
    void arrayParameterUsesFirstValue() throws Exception {
        mockMvc.perform(get("/api/items/search?tag[]=계산&tag[]=문장제&unit[x]=M5-1")).andExpect(status().isOk());

        ItemSearchCommand command = capturedCommand();
        assertThat(command.tag()).isEqualTo("계산");
        assertThat(command.unit()).isEqualTo("M5-1");
    }
}
