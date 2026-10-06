package com.example.domain.item;

import java.util.List;
import java.util.Optional;

/** 문항 저장소. 구현은 {@code infrastructure.item} 에 둔다. */
public interface ItemRepository {

    /** 단건 조회 — 단원 · 태그를 함께 가져온다(OSIV 꺼져 있음). */
    Optional<Item> findWithDetailsById(Integer id);

    /** 단원 코드 + 상태로 조회. 정렬은 난이도 내림차순, 같은 난이도면 id 오름차순. 단원 · 태그를 함께 가져온다. */
    List<Item> findByUnitCodeAndStatus(String unitCode, String status);

    long countByUnitIdAndStatus(Integer unitId, String status);

    /** 문항 검색 — 공개({@code status='A'}) 문항 중 조건에 맞는 전체 건수와 현재 페이지의 행. */
    ItemSearchPage search(ItemSearchCondition condition);
}
