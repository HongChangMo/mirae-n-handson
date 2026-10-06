package com.example.domain.item;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * 레거시(PHP 7.4) 문자열 처리를 그대로 옮긴 도우미. 입력값 해석이 레거시와 한 글자도 다르지 않게 하려고 둔다.
 * 근거: {@code legacy/item-bank-php/search.php:85-87, :211, :228, :266, :337}.
 */
public final class PhpStrings {

    /** PHP {@code trim()} 이 기본으로 지우는 문자: 공백 · \t · \n · \r · \0 · \x0B. 유니코드 공백은 지우지 않는다. */
    private static final String TRIM_CHARACTERS = " \t\n\r\0\u000B";

    /** PHP 숫자 문자열 앞에서 건너뛰는 공백: 공백 · \t · \n · \r · \v · \f. */
    private static final String NUMERIC_LEADING_WHITESPACE = " \t\n\r\u000B\f";

    private PhpStrings() {
    }

    /** PHP {@code trim($s)}. */
    public static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && TRIM_CHARACTERS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && TRIM_CHARACTERS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    /** PHP {@code mb_strlen($s, 'UTF-8')} — 코드포인트 수. */
    public static int mbLength(String value) {
        return value.codePointCount(0, value.length());
    }

    /** PHP {@code mb_substr($s, 0, $length, 'UTF-8')} — 앞에서 코드포인트 {@code length} 개. */
    public static String mbSubstr(String value, int length) {
        if (mbLength(value) <= length) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, length));
    }

    /** PHP {@code strtolower()} — 기본 로캘(C)에서는 ASCII 영문만 바꾼다. */
    public static String asciiLower(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return out.toString();
    }

    /**
     * PHP {@code preg_match('/^<regex>$/', $s)}. PHP 의 {@code $} 는 문자열 끝의 줄바꿈 하나 앞에서도 맞는다.
     */
    public static boolean matchesLine(String regex, String value) {
        return Pattern.compile("(?:" + regex + ")\n?").matcher(value).matches();
    }

    /**
     * PHP 7.4 {@code (int)$s}. 앞쪽 공백을 건너뛰고 읽을 수 있는 만큼의 숫자(부호 · 소수점 · 지수 포함)만 쓴다.
     * 숫자가 없으면 0, long 범위를 넘는 정수는 PHP_INT_MAX · PHP_INT_MIN, 무한대가 되는 지수는 0.
     */
    public static long toInt(String value) {
        int i = 0;
        int n = value.length();
        while (i < n && NUMERIC_LEADING_WHITESPACE.indexOf(value.charAt(i)) >= 0) {
            i++;
        }
        int start = i;
        if (i < n && (value.charAt(i) == '+' || value.charAt(i) == '-')) {
            i++;
        }
        int intDigitsStart = i;
        while (i < n && isDigit(value.charAt(i))) {
            i++;
        }
        boolean hasIntDigits = i > intDigitsStart;
        boolean isDouble = false;
        if (i + 1 < n && value.charAt(i) == '.' && isDigit(value.charAt(i + 1))) {
            isDouble = true;
            i++;
            while (i < n && isDigit(value.charAt(i))) {
                i++;
            }
        } else if (hasIntDigits && i < n && value.charAt(i) == '.') {
            isDouble = true;
            i++;
        }
        if (!hasIntDigits && !isDouble) {
            return 0L;
        }
        if (i < n && (value.charAt(i) == 'e' || value.charAt(i) == 'E')) {
            int exp = i + 1;
            if (exp < n && (value.charAt(exp) == '+' || value.charAt(exp) == '-')) {
                exp++;
            }
            if (exp < n && isDigit(value.charAt(exp))) {
                isDouble = true;
                i = exp;
                while (i < n && isDigit(value.charAt(i))) {
                    i++;
                }
            }
        }
        String number = value.substring(start, i);
        if (!isDouble) {
            BigInteger parsed = new BigInteger(number);
            if (parsed.bitLength() < Long.SIZE) {
                return parsed.longValue();
            }
            return parsed.signum() > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return doubleToLongCap(Double.parseDouble(number));
    }

    /** PHP {@code zend_dval_to_lval_cap}. */
    private static long doubleToLongCap(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return 0L;
        }
        if (value >= 0x1p63 || value < -0x1p63) {
            return value > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return (long) value;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
