package com.example.application.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.domain.item.ItemRepository;
import com.example.domain.item.ItemSearchCondition;
import com.example.domain.item.ItemSearchPage;
import com.example.domain.item.ItemSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    @Test
    @DisplayName("search: 요청 값을 레거시 규칙대로 해석한 조건으로 저장소에 묻는다")
    void searchBuildsConditionFromCommand() {
        ItemSearchCommand command = new ItemSearchCommand(" 분수 ", "M5-1", "3a", "계산", "unit", "desc", "1000");
        ItemSearchCondition expected = ItemSearchCondition.of(" 분수 ", "M5-1", "3a", "계산", "unit", "desc", "1000");
        when(itemRepository.search(expected)).thenReturn(new ItemSearchPage(0, List.of()));

        itemSearchService.search(command);

        verify(itemRepository).search(expected);
    }

    @Test
    @DisplayName("search: 건수와 행(단원 코드 · 태그 순서)을 그대로 옮긴다")
    void searchMapsPage() {
        ItemSearchCommand command = new ItemSearchCommand(null, null, null, null, null, null, null);
        when(itemRepository.search(ItemSearchCondition.of(null, null, null, null, null, null, null)))
            .thenReturn(new ItemSearchPage(21, List.of(
                new ItemSummary(18, "분수의 뺄셈 문장제", "M5-1", 4, List.of("문장제", "오답률높음")),
                new ItemSummary(19, "세 분수의 곱셈", "M5-2", 4, List.of()))));

        ItemSearchResult result = itemSearchService.search(command);

        assertThat(result.count()).isEqualTo(21);
        assertThat(result.rows()).containsExactly(
            new ItemSearchResult.Row(18, "분수의 뺄셈 문장제", "M5-1", 4, List.of("문장제", "오답률높음")),
            new ItemSearchResult.Row(19, "세 분수의 곱셈", "M5-2", 4, List.of()));
    }
}
