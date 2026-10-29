package io.github.aw1y2z.sesame.data.plan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 单个每日计划：一个模块/风险域，固定时间窗 + 若干明确轮次时间。 */
public class Plan {

    public static final String MODE_ONCE = "ONCE";
    public static final String MODE_COLLECT = "COLLECT";

    private String id = UUID.randomUUID().toString();
    private String target;
    private String windowStart = "07:00";
    private String windowEnd = "09:00";
    private boolean enabled = true;
    /** ONCE=当天做一次；COLLECT=可多轮收集（如森林能量）。 */
    private String mode = MODE_ONCE;
    /** >0 时把任务列表平均铺到这么多分钟内完成；0=按模块自身节奏。 */
    private int paceMinutes = 0;
    /** 默认编排生成的计划；用户编辑后清掉。 */
    private boolean defaultPlan = false;
    /** 所属组（日常）id；空表示独立计划。 */
    private String groupId = "";
    /** 按类型执行的过滤器 JSON；空表示整域全跑。 */
    private String filters = "";
    private final List<String> roundTimes = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(String windowStart) {
        this.windowStart = windowStart;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = MODE_COLLECT.equals(mode) ? MODE_COLLECT : MODE_ONCE;
    }

    public boolean isCollect() {
        return MODE_COLLECT.equals(mode);
    }

    public int getPaceMinutes() {
        return paceMinutes;
    }

    public void setPaceMinutes(int paceMinutes) {
        this.paceMinutes = Math.max(0, Math.min(120, paceMinutes));
    }

    public boolean isDefaultPlan() {
        return defaultPlan;
    }

    public void setDefaultPlan(boolean defaultPlan) {
        this.defaultPlan = defaultPlan;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters == null ? "" : filters;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId == null ? "" : groupId;
    }

    public List<String> getRoundTimes() {
        return roundTimes;
    }

    public void setRoundTimes(List<String> times) {
        // 先拷贝：normalize() 会传 this.roundTimes 本身，直接 clear 会把入参一起清空
        List<String> input = times == null ? new ArrayList<>() : new ArrayList<>(times);
        roundTimes.clear();
        for (String time : input) {
            if (PlanTargets.isValidTime(time) && !roundTimes.contains(time)) {
                roundTimes.add(time);
            }
        }
        Collections.sort(roundTimes);
    }

    public void normalize() {
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (target == null) {
            target = "";
        }
        if (!PlanTargets.isValidTime(windowStart)) {
            windowStart = "07:00";
        }
        if (!PlanTargets.isValidTime(windowEnd)) {
            windowEnd = "09:00";
        }
        if (mode == null || (!MODE_ONCE.equals(mode) && !MODE_COLLECT.equals(mode))) {
            mode = defaultModeFor(target);
        }
        setPaceMinutes(paceMinutes);
        setRoundTimes(roundTimes);
        // 有任务列表的目标做完即结束，再排多轮只会重复空跑；统一压成一轮。
        if (PlanTargetLedgers.hasTaskList(target)) {
            mode = MODE_ONCE;
            if (roundTimes.size() > 1) {
                String first = roundTimes.get(0);
                roundTimes.clear();
                roundTimes.add(first);
            }
        }
    }

    private static String defaultModeFor(String target) {
        return PlanTargets.FOREST_COLLECT.equals(target) ? MODE_COLLECT : MODE_ONCE;
    }

    public JSONObject toJson() {
        normalize();
        JSONObject jo = new JSONObject();
        try {
            jo.put("id", id);
            jo.put("target", target);
            jo.put("windowStart", windowStart);
            jo.put("windowEnd", windowEnd);
            jo.put("enabled", enabled);
            jo.put("mode", mode);
            jo.put("paceMinutes", paceMinutes);
            jo.put("defaultPlan", defaultPlan);
            if (groupId != null && !groupId.isEmpty()) {
                jo.put("groupId", groupId);
            }
            if (filters != null && !filters.isEmpty()) {
                jo.put("filters", filters);
            }
            JSONArray times = new JSONArray();
            for (String time : roundTimes) {
                times.put(time);
            }
            jo.put("roundTimes", times);
        } catch (Throwable ignored) {
        }
        return jo;
    }

    public static Plan fromJson(JSONObject jo) {
        if (jo == null) {
            return null;
        }
        Plan plan = new Plan();
        plan.id = jo.optString("id", UUID.randomUUID().toString());
        plan.target = jo.optString("target", "");
        plan.windowStart = jo.optString("windowStart", "07:00");
        plan.windowEnd = jo.optString("windowEnd", "09:00");
        plan.enabled = jo.optBoolean("enabled", true);
        plan.mode = jo.optString("mode", defaultModeFor(plan.target));
        plan.paceMinutes = jo.optInt("paceMinutes", 0);
        plan.defaultPlan = jo.optBoolean("defaultPlan", false);
        plan.groupId = jo.optString("groupId", "");
        plan.filters = jo.optString("filters", "");
        List<String> times = new ArrayList<>();
        JSONArray array = jo.optJSONArray("roundTimes");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                times.add(array.optString(i));
            }
        }
        plan.setRoundTimes(times);
        plan.normalize();
        return plan;
    }
}
