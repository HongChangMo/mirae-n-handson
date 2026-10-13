package com.example.domain.item;

/**
 * 난이도 조건. 둘 중 하나만 값이 있다.
 * <ul>
 *   <li>{@code exclusiveMax} — 난이도 파라미터가 비었을 때 {@code level < exclusiveMax} (BR-10)</li>
 *   <li>{@code equalTo} — 난이도가 있을 때 {@code level = equalTo} (BR-11, BR-12)</li>
 * </ul>
 */
public record LevelFilter(Integer exclusiveMax, Long equalTo) {

    public static LevelFilter belowMax(int exclusiveMax) {
        return new LevelFilter(exclusiveMax, null);
    }

    public static LevelFilter equalTo(long level) {
        return new LevelFilter(null, level);
    }

    public boolean isBelowMax() {
        return exclusiveMax != null;
    }
}
