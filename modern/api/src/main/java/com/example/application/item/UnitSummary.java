package com.example.application.item;

import com.example.domain.item.Unit;

/** 단원 조회 결과. */
public record UnitSummary(Integer id, String code, String name, Integer grade) {

    static UnitSummary from(Unit unit) {
        return new UnitSummary(unit.getId(), unit.getCode(), unit.getName(), unit.getGrade());
    }
}
