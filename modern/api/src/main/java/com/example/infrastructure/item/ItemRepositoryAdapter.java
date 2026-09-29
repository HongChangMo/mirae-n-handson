package com.example.infrastructure.item;

import com.example.domain.item.Item;
import com.example.domain.item.ItemRepository;
import com.example.domain.item.ItemSearchCondition;
import com.example.domain.item.ItemSearchPage;
import com.example.domain.item.ItemStatus;
import com.example.domain.item.ItemSummary;
import com.example.domain.item.LevelFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link ItemRepository} 구현. 기존 조회는 Spring Data 저장소에 넘기고, 검색은 JPQL 로 직접 조립한다.
 *
 * <p>검색은 요청마다 쿼리 3개로 끝난다: 건수 → 현재 페이지 행 → 그 행들의 태그. 행 수에 비례하는 쿼리는 없다.
 * 레거시는 뷰 {@code v_item_public} 을 썼다. 뷰의 조건(공개 상태, 단원 INNER JOIN, 태그 id 순)을 여기 JPQL 로 옮겼다.
 */
@Repository
public class ItemRepositoryAdapter implements ItemRepository {

    private final ItemJpaRepository itemJpaRepository;
    private final EntityManager entityManager;

    public ItemRepositoryAdapter(ItemJpaRepository itemJpaRepository, EntityManager entityManager) {
        this.itemJpaRepository = itemJpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Item> findWithDetailsById(Integer id) {
        return itemJpaRepository.findWithDetailsById(id);
    }

    @Override
    public List<Item> findByUnitCodeAndStatus(String unitCode, String status) {
        return itemJpaRepository.findByUnitCodeAndStatus(unitCode, status);
    }

    @Override
    public long countByUnitIdAndStatus(Integer unitId, String status) {
        return itemJpaRepository.countByUnitIdAndStatus(unitId, status);
    }

    @Override
    public ItemSearchPage search(ItemSearchCondition condition) {
        Map<String, Object> params = new HashMap<>();
        String from = searchFrom(condition, params);

        TypedQuery<Long> countQuery = entityManager.createQuery("select count(i)" + from, Long.class);
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();
        if (total == 0) {
            return new ItemSearchPage(0, List.of());
        }

        TypedQuery<Object[]> rowQuery = entityManager.createQuery(
            "select i.id, i.title, u.code, i.level" + from + " order by " + condition.sortOrder().jpql(),
            Object[].class);
        params.forEach(rowQuery::setParameter);
        rowQuery.setFirstResult(condition.offset());
        rowQuery.setMaxResults(ItemSearchCondition.PAGE_SIZE);
        List<Object[]> rows = rowQuery.getResultList();
        if (rows.isEmpty()) {
            return new ItemSearchPage(total, List.of());
        }

        Map<Integer, List<String>> tagNames = tagNamesOf(rows.stream().map(row -> (Integer) row[0]).toList());
        List<ItemSummary> summaries = rows.stream()
            .map(row -> new ItemSummary(
                (Integer) row[0],
                (String) row[1],
                (String) row[2],
                (Integer) row[3],
                tagNames.getOrDefault((Integer) row[0], List.of())))
            .toList();
        return new ItemSearchPage(total, summaries);
    }

    /** FROM · WHERE 절. 조건은 모두 AND (BR-02). 값은 전부 바인딩 파라미터로 넘긴다. */
    private static String searchFrom(ItemSearchCondition condition, Map<String, Object> params) {
        StringBuilder jpql = new StringBuilder(" from Item i join i.unit u where i.status = :active");
        params.put("active", ItemStatus.ACTIVE);

        if (condition.keyword() != null) {
            // % · _ 를 이스케이프하지 않는다 — 레거시처럼 와일드카드로 동작 (BR-06)
            jpql.append(" and (i.title like :keyword or i.stem like :keyword)");
            params.put("keyword", "%" + condition.keyword() + "%");
        }
        if (condition.unitCode() != null) {
            jpql.append(" and u.code = :unitCode");
            params.put("unitCode", condition.unitCode());
        }
        LevelFilter level = condition.level();
        if (level.isBelowMax()) {
            jpql.append(" and i.level < :levelMax");
            params.put("levelMax", level.exclusiveMax());
        } else if (fitsInInt(level.equalTo())) {
            jpql.append(" and i.level = :level");
            params.put("level", Math.toIntExact(level.equalTo()));
        } else {
            // level 컬럼(TINYINT)은 int 범위를 넘는 값과 같을 수 없다 — 레거시도 0건
            jpql.append(" and 1 = 0");
        }
        if (condition.tag() != null) {
            jpql.append(" and exists (select 1 from Item x join x.tags t where x.id = i.id and t.name = :tag)");
            params.put("tag", condition.tag());
        }
        return jpql.toString();
    }

    /** 문항 id → 태그 이름 목록(태그 id 순, BR-30). 쿼리 한 번. */
    private Map<Integer, List<String>> tagNamesOf(List<Integer> itemIds) {
        List<Object[]> pairs = entityManager.createQuery(
                "select i.id, t.name from Item i join i.tags t where i.id in :ids order by t.id asc",
                Object[].class)
            .setParameter("ids", itemIds)
            .getResultList();
        Map<Integer, List<String>> byItem = new LinkedHashMap<>();
        for (Object[] pair : pairs) {
            byItem.computeIfAbsent((Integer) pair[0], id -> new ArrayList<>()).add((String) pair[1]);
        }
        return byItem;
    }

    private static boolean fitsInInt(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }
}
