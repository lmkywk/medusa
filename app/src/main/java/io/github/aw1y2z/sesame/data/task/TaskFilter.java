package io.github.aw1y2z.sesame.data.task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 按类型执行：只放行指定机制/成本/域/类键的任务。
 *
 * <p>任何维度为空＝不限。全空过滤器等同于“放行全部”，调用方应直接不传，
 * 以保证旧计划和单目标执行完全不变。
 */
public final class TaskFilter {

    private final Set<String> mechanisms = new LinkedHashSet<>();
    private final Set<String> costTypes = new LinkedHashSet<>();
    private final Set<String> domains = new LinkedHashSet<>();
    private final Set<String> classKeys = new LinkedHashSet<>();

    public static TaskFilter of(Collection<String> mechanisms, Collection<String> costTypes,
                                Collection<String> domains, Collection<String> classKeys) {
        TaskFilter filter = new TaskFilter();
        addAll(filter.mechanisms, mechanisms);
        addAll(filter.costTypes, costTypes);
        addAll(filter.domains, domains);
        addAll(filter.classKeys, classKeys);
        return filter.isEmpty() ? null : filter;
    }

    private static void addAll(Set<String> target, Collection<String> source) {
        if (source == null) {
            return;
        }
        for (String item : source) {
            if (item != null && !item.trim().isEmpty()) {
                target.add(item.trim().toUpperCase(Locale.ROOT));
            }
        }
    }

    public boolean isEmpty() {
        return mechanisms.isEmpty() && costTypes.isEmpty() && domains.isEmpty() && classKeys.isEmpty();
    }

    /**
     * 放行判断：给了 classKeys 就只认精确类；否则机制/成本/域三者必须同时满足。
     * 全空已在 {@link #isEmpty()} 处短路为放行。
     */
    public boolean allows(TaskCapabilityRegistry.Spec spec) {
        if (spec == null || isEmpty()) {
            return true;
        }
        if (!classKeys.isEmpty()) {
            return classKeys.contains(spec.classKey.toUpperCase(Locale.ROOT));
        }
        if (!mechanisms.isEmpty()
                && !mechanisms.contains(spec.mechanism.name().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (!costTypes.isEmpty()
                && !costTypes.contains(spec.costType.name().toUpperCase(Locale.ROOT))) {
            return false;
        }
        return domains.isEmpty() || domains.contains(domainOf(spec.classKey));
    }

    private static String domainOf(String classKey) {
        if (classKey == null) {
            return "";
        }
        int first = classKey.indexOf('|');
        int second = first < 0 ? -1 : classKey.indexOf('|', first + 1);
        return second < 0 ? "" : classKey.substring(first + 1, second).toUpperCase(Locale.ROOT);
    }

    public JSONObject toJson() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("mechanisms", new JSONArray(mechanisms));
            jo.put("costTypes", new JSONArray(costTypes));
            jo.put("domains", new JSONArray(domains));
            jo.put("classKeys", new JSONArray(classKeys));
        } catch (Throwable ignored) {
        }
        return jo;
    }

    public static TaskFilter fromJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return fromJson(new JSONObject(raw));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static TaskFilter fromJson(JSONObject jo) {
        if (jo == null) {
            return null;
        }
        return of(read(jo.optJSONArray("mechanisms")), read(jo.optJSONArray("costTypes")),
                read(jo.optJSONArray("domains")), read(jo.optJSONArray("classKeys")));
    }

    private static Set<String> read(JSONArray array) {
        if (array == null) {
            return null;
        }
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }
}
