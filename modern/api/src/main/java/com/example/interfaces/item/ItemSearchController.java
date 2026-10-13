package com.example.interfaces.item;

import com.example.application.item.ItemSearchService;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 검색 API — 레거시 {@code search.php} 이관. 파라미터 이름은 레거시와 같다.
 * 잘못된 입력도 오류로 돌려주지 않고 레거시처럼 조회 결과(0건일 수 있음)를 돌려준다.
 */
@RestController
@RequestMapping("/api/items")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;

    public ItemSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /** {@code GET /api/items/search?q=&unit=&level=&tag=&sort=&dir=&page=} */
    @GetMapping("/search")
    public ItemSearchResponse search(@RequestParam MultiValueMap<String, String> params) {
        return ItemSearchResponse.from(itemSearchService.search(ItemSearchParams.toCommand(params)));
    }
}
