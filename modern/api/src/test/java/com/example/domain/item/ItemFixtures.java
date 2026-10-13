package com.example.domain.item;

import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/** 테스트용 엔티티 생성 도우미. */
public final class ItemFixtures {

    public static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    private ItemFixtures() {
    }

    public static Unit unit(int id, String code, String name, int grade) {
        return new Unit(id, code, name, grade);
    }

    public static Tag tag(int id, String name) {
        return new Tag(id, name);
    }

    public static Item item(Integer id, Unit unit, String title, int level, String status, Tag... tags) {
        Item item = new Item(unit, title, title + " 문제 본문", level, status, SEED_TIME);
        for (Tag tag : tags) {
            item.addTag(tag);
        }
        if (id != null) {
            ReflectionTestUtils.setField(item, "id", id);
        }
        return item;
    }
}
