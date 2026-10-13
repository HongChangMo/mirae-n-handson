package com.example.interfaces.item;

import com.example.application.item.UnitSummary;

/** {@code GET /api/units} 응답 항목. */
public record UnitResponse(Integer id, String code, String name, Integer grade) {

    static UnitResponse from(UnitSummary unit) {
        return new UnitResponse(unit.id(), unit.code(), unit.name(), unit.grade());
    }
}
