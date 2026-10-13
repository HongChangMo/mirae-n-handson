package com.example.interfaces.item;

import com.example.application.item.ItemDetail;
import java.util.List;

/**
 * 문항 응답. {@code unit} 은 단원 코드({@code M5-1}), {@code tags} 는 태그 이름 목록(id 순).
 */
public record ItemResponse(
    Integer id,
    String title,
    String stem,
    String unit,
    Integer level,
    String status,
    List<String> tags) {

    static ItemResponse from(ItemDetail item) {
        return new ItemResponse(
            item.id(),
            item.title(),
            item.stem(),
            item.unit(),
            item.level(),
            item.status(),
            item.tags());
    }
}
