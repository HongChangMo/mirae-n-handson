package com.example.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhpStringsTest {

    @Test
    @DisplayName("trim: PHP 기본 문자(공백 · \\t · \\n · \\r · \\0 · \\x0B)만 지우고 전각 공백은 남긴다")
    void trimRemovesOnlyPhpDefaultCharacters() {
        assertThat(PhpStrings.trim(" \t\n\r\0\u000B분수 \t")).isEqualTo("분수");
        assertThat(PhpStrings.trim("　분수　")).isEqualTo("　분수　");
        assertThat(PhpStrings.trim("   ")).isEmpty();
    }

    @Test
    @DisplayName("mbLength · mbSubstr: UTF-16 단위가 아니라 문자(코드포인트) 단위로 센다")
    void countsCodePoints() {
        String emoji = "😀😀😀";
        assertThat(PhpStrings.mbLength(emoji)).isEqualTo(3);
        assertThat(PhpStrings.mbSubstr(emoji, 2)).isEqualTo("😀😀");
        assertThat(PhpStrings.mbSubstr("가나", 5)).isEqualTo("가나");
    }

    @Test
    @DisplayName("toInt: PHP 7.4 (int) 변환 — 앞쪽 숫자만 읽고, 소수 · 지수는 버림, 숫자가 없으면 0")
    void toIntFollowsPhpCast() {
        assertThat(PhpStrings.toInt("3")).isEqualTo(3L);
        assertThat(PhpStrings.toInt("3a")).isEqualTo(3L);
        assertThat(PhpStrings.toInt("3.7")).isEqualTo(3L);
        assertThat(PhpStrings.toInt("1e3")).isEqualTo(1000L);
        assertThat(PhpStrings.toInt("-2")).isEqualTo(-2L);
        assertThat(PhpStrings.toInt("+4")).isEqualTo(4L);
        assertThat(PhpStrings.toInt(".9")).isEqualTo(0L);
        assertThat(PhpStrings.toInt("abc")).isEqualTo(0L);
        assertThat(PhpStrings.toInt("")).isEqualTo(0L);
        assertThat(PhpStrings.toInt("2\n")).isEqualTo(2L);
    }

    @Test
    @DisplayName("toInt: long 범위를 넘으면 PHP_INT_MAX · PHP_INT_MIN, 무한대는 0")
    void toIntCapsOverflow() {
        assertThat(PhpStrings.toInt("99999999999999999999")).isEqualTo(Long.MAX_VALUE);
        assertThat(PhpStrings.toInt("-99999999999999999999")).isEqualTo(Long.MIN_VALUE);
        assertThat(PhpStrings.toInt("1e1000")).isEqualTo(0L);
    }

    @Test
    @DisplayName("matchesLine: preg_match 의 $ 처럼 끝의 줄바꿈 하나는 허용한다")
    void matchesLineAllowsTrailingNewline() {
        assertThat(PhpStrings.matchesLine("[0-9]+", "12")).isTrue();
        assertThat(PhpStrings.matchesLine("[0-9]+", "12\n")).isTrue();
        assertThat(PhpStrings.matchesLine("[0-9]+", "12\n\n")).isFalse();
        assertThat(PhpStrings.matchesLine("[0-9]+", " 12")).isFalse();
        assertThat(PhpStrings.matchesLine("[0-9]+", "-1")).isFalse();
    }

    @Test
    @DisplayName("asciiLower: ASCII 영문만 소문자로 바꾼다(PHP strtolower)")
    void asciiLowerChangesAsciiOnly() {
        assertThat(PhpStrings.asciiLower("LEVEL")).isEqualTo("level");
        assertThat(PhpStrings.asciiLower("ÄB")).isEqualTo("Äb");
    }
}
