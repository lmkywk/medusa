package io.github.aw1y2z.sesame.data.plan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/** 计划列表的共享存储：模块 App 编辑，注入进程读取。 */
public final class PlanStore {

    public static final int SCHEMA_VERSION = 2;
    /** 默认编排布局版本；升级后老用户若从未改过默认计划，自动换成新的细粒度排布。 */
    private static final int DEFAULT_LAYOUT_VERSION = 5;

    private static final String TAG = "PlanStore";
    private static final Object LOCK = new Object();

    private PlanStore() {
    }

    public static List<Plan> load(String userId) {
        synchronized (LOCK) {
            List<Plan> plans = new ArrayList<>();
            File file = FileUtil.getPlanFile(userId);
            try {
                if (!file.exists()) {
                    return plans;
                }
                String json = FileUtil.readFromFile(file);
                if (json == null || json.trim().isEmpty()) {
                    return plans;
                }
                JSONObject root = new JSONObject(json);
                JSONArray array = root.optJSONArray("plans");
                if (array == null) {
                    return plans;
                }
                for (int i = 0; i < array.length(); i++) {
                    Plan plan = Plan.fromJson(array.optJSONObject(i));
                    if (plan != null && PlanTargets.isValidTarget(plan.getTarget()) && !plan.getRoundTimes().isEmpty()) {
                        plans.add(plan);
                    }
                }
                ensureDefaultLayout(userId, root, plans);
                ensureDefaultSchedule(userId, root, plans);
            } catch (Throwable t) {
                Log.err(TAG, "读取计划失败", t);
            }
            return plans;
        }
    }

    /**
     * 默认布局升级：只替换“全部还是默认计划”的账号；用户改过/新建过计划就保持原样，
     * 只记下版本，避免每次启动都尝试覆盖。
     */
    private static void ensureDefaultLayout(String userId, JSONObject root, List<Plan> plans) {
        try {
            JSONObject defaults = root.optJSONObject("defaults");
            int version = defaults == null ? 0 : defaults.optInt("layoutVersion", 0);
            if (version >= DEFAULT_LAYOUT_VERSION) {
                return;
            }
            boolean allDefault = !plans.isEmpty();
            for (Plan plan : plans) {
                if (!plan.isDefaultPlan()) {
                    allDefault = false;
                    break;
                }
            }
            if (defaults == null) {
                defaults = new JSONObject();
            }
            defaults.put("layoutVersion", DEFAULT_LAYOUT_VERSION);
            if (plans.isEmpty() || allDefault) {
                List<Plan> fresh = DefaultSchedule.createPlans();
                plans.clear();
                plans.addAll(fresh);
                defaults.put("dailySchedule", true);
                JSONArray array = new JSONArray();
                for (Plan plan : plans) {
                    array.put(plan.toJson());
                }
                root.put("plans", array);
                Log.record("计划📋默认编排升级到布局 v" + DEFAULT_LAYOUT_VERSION + "（" + plans.size() + " 项）");
            }
            root.put("defaults", defaults);
            root.put("schemaVersion", SCHEMA_VERSION);
            FileUtil.write2File(root.toString(), FileUtil.getPlanFile(userId));
        } catch (Throwable t) {
            Log.err(TAG, "升级默认计划布局失败", t);
        }
    }

    /**
     * 默认编排只给“没有任何计划”的用户自动生成；已有自定义计划的用户不动，
     * 只记下标记，之后可在管理计划里手动“恢复默认编排”。
     */
    private static void ensureDefaultSchedule(String userId, JSONObject root, List<Plan> plans) {
        JSONObject defaults = root.optJSONObject("defaults");
        if (defaults != null && defaults.optBoolean("dailySchedule", false)) {
            return;
        }
        boolean added = false;
        if (plans.isEmpty()) {
            plans.addAll(DefaultSchedule.createPlans());
            added = true;
        }
        writeDefaults(userId, root, plans, defaults, "dailySchedule", added);
    }

    /** 用默认编排替换现有计划；调用方负责确认。 */
    public static boolean restoreDefaults(String userId) {
        synchronized (LOCK) {
            List<Plan> defaults = DefaultSchedule.createPlans();
            boolean saved = save(userId, defaults);
            if (saved) {
                try {
                    JSONObject root = new JSONObject();
                    root.put("version", 1);
                    root.put("schemaVersion", SCHEMA_VERSION);
                    JSONObject flags = readDefaults(userId);
                    if (flags == null) {
                        flags = new JSONObject();
                    }
                    flags.put("dailySchedule", true);
                    root.put("defaults", flags);
                    JSONArray array = new JSONArray();
                    for (Plan plan : defaults) {
                        array.put(plan.toJson());
                    }
                    root.put("plans", array);
                    FileUtil.write2File(root.toString(), FileUtil.getPlanFile(userId));
                } catch (Throwable t) {
                    Log.err(TAG, "恢复默认计划标记失败", t);
                }
            }
            return saved;
        }
    }

    private static void writeDefaults(String userId, JSONObject root, List<Plan> plans,
                                      JSONObject defaults, String key, boolean added) {
        try {
            if (defaults == null) {
                defaults = new JSONObject();
            }
            defaults.put(key, true);
            root.put("defaults", defaults);
            root.put("schemaVersion", SCHEMA_VERSION);
            if (added) {
                JSONArray array = new JSONArray();
                for (Plan plan : plans) {
                    array.put(plan.toJson());
                }
                root.put("plans", array);
            }
            FileUtil.write2File(root.toString(), FileUtil.getPlanFile(userId));
        } catch (Throwable t) {
            Log.err(TAG, "写入默认计划失败", t);
        }
    }

    public static List<Plan> loadEnabled(String userId) {
        List<Plan> result = new ArrayList<>();
        for (Plan plan : load(userId)) {
            if (plan.isEnabled() && !plan.getRoundTimes().isEmpty()) {
                result.add(plan);
            }
        }
        return result;
    }

    public static boolean save(String userId, List<Plan> plans) {
        synchronized (LOCK) {
            try {
                JSONObject root = new JSONObject();
                root.put("version", 1);
                root.put("schemaVersion", SCHEMA_VERSION);
                // 保留一次性迁移标记：用户删掉默认「森林·日常」后，下次读取不能又自动补回来。
                JSONObject defaults = readDefaults(userId);
                if (defaults != null) {
                    root.put("defaults", defaults);
                }
                JSONArray array = new JSONArray();
                if (plans != null) {
                    for (Plan plan : plans) {
                        if (plan != null) {
                            plan.normalize();
                            array.put(plan.toJson());
                        }
                    }
                }
                root.put("plans", array);
                return FileUtil.write2File(root.toString(), FileUtil.getPlanFile(userId));
            } catch (Throwable t) {
                Log.err(TAG, "保存计划失败", t);
                return false;
            }
        }
    }

    private static JSONObject readDefaults(String userId) {
        try {
            File file = FileUtil.getPlanFile(userId);
            if (!file.exists()) {
                return null;
            }
            String json = FileUtil.readFromFile(file);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            return new JSONObject(json).optJSONObject("defaults");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
