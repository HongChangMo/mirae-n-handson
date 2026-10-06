package com.example.application.item;

import com.example.domain.item.ItemRepository;
import com.example.domain.item.ItemSearchCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 — 레거시 {@code legacy/item-bank-php/search.php} 이관.
 * 입력이 잘못돼도 거부하지 않고 조회한다(레거시와 같게 0건일 수 있다).
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemRepository itemRepository;

    public ItemSearchService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public ItemSearchResult search(ItemSearchCommand command) {
        ItemSearchCondition condition = ItemSearchCondition.of(
            command.q(), command.unit(), command.level(), command.tag(),
            command.sort(), command.dir(), command.page());
        ItemSearchResult result = ItemSearchResult.from(itemRepository.search(condition));
        log.debug("item search: page {} returned {} of {} rows", condition.page(), result.rows().size(),
            result.count());
        return result;
    }
}
