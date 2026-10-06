package com.example.interfaces.item;

import com.example.application.item.ItemSearchCommand;
import java.util.List;
import java.util.Map;
import org.springframework.util.MultiValueMap;

/**
 * 요청 파라미터를 PHP {@code $_GET} 과 같은 방식으로 꺼낸다.
 * <ul>
 *   <li>{@code name[]=a&name[]=b} · {@code name[x]=a} — 배열이므로 첫 값 (BR-03, {@code search.php:53-80})</li>
 *   <li>{@code name=a&name=b} — 뒤 값이 앞 값을 덮으므로 마지막 값</li>
 *   <li>없으면 {@code null}, 빈 값이면 {@code ""}</li>
 * </ul>
 */
final class ItemSearchParams {

    private ItemSearchParams() {
    }

    static ItemSearchCommand toCommand(MultiValueMap<String, String> params) {
        return new ItemSearchCommand(
            value(params, "q"),
            value(params, "unit"),
            value(params, "level"),
            value(params, "tag"),
            value(params, "sort"),
            value(params, "dir"),
            value(params, "page"));
    }

    private static String value(MultiValueMap<String, String> params, String name) {
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            if (isArrayKey(entry.getKey(), name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        List<String> values = params.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(values.size() - 1);
    }

    /** {@code name[]} · {@code name[x]} 처럼 PHP 가 배열로 받는 키인지. */
    private static boolean isArrayKey(String key, String name) {
        return key.startsWith(name + "[") && key.indexOf(']', name.length()) > 0;
    }
}
