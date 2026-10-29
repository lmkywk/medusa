package io.github.aw1y2z.sesame.data.task;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import io.github.aw1y2z.sesame.data.metrics.RpcMetrics;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 端点级状态（代表验证）：今天每个 RPC 方法第一次被调用，就是该类的 canary。
 *
 * <p>只记录“方法与结果”，不含标题/参数。状态按天保存，00:00 自动换日。
 * 失败会带一个短冷却，供手动队列提示“这一类今天不通”；不拦截执行——
 * 手动直达仍以用户选择为准，拦截留给后续 TaskFilter。
 */
public final class EndpointStateStore {

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    private static final String FILE_NAME = "capability_state.json";
    private static final long FAIL_TTL_MS = 2L * 60L * 60L * 1000L;
    private static final Object LOCK = new Object();

    private EndpointStateStore() {
    }

    private static String dayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    private static File file() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, FILE_NAME);
    }

    private static JSONObject loadRoot() {
        JSONObject root = null;
        try {
            String raw = FileUtil.readFromFile(file());
            if (raw != null && !raw.trim().isEmpty()) {
                root = new JSONObject(raw);
            }
        } catch (Throwable ignored) {
        }
        if (root == null) {
            root = new JSONObject();
        }
        if (!dayKey().equals(root.optString("day", ""))) {
            try {
                root = new JSONObject();
                root.put("day", dayKey());
                root.put("schemaVersion", 1);
                root.put("methods", new JSONObject());
            } catch (Throwable ignored) {
            }
        }
        return root;
    }

    /** RPC 收口调用：把一次请求的结果并入端点状态。 */
    public static void observe(String method, RpcMetrics.Outcome outcome) {
        if (method == null || method.trim().isEmpty() || outcome == null) {
            return;
        }
        if (outcome == RpcMetrics.Outcome.BLOCKED) {
            return;
        }
        synchronized (LOCK) {
            try {
                JSONObject root = loadRoot();
                JSONObject methods = root.optJSONObject("methods");
                if (methods == null) {
                    methods = new JSONObject();
                    root.put("methods", methods);
                }
                String key = normalize(method);
                JSONObject item = methods.optJSONObject(key);
                if (item == null) {
                    item = new JSONObject();
                    item.put("method", key);
                    item.put("ok", 0);
                    item.put("fail", 0);
                    item.put("c1009", 0);
                    item.put("c1004", 0);
                    item.put("state", "");
                    item.put("strikes", 0);
                    item.put("until", 0L);
                    item.put("firstAt", System.currentTimeMillis());
                    methods.put(key, item);
                }
                long now = System.currentTimeMillis();
                item.put("lastAt", now);
                switch (outcome) {
                    case OK:
                        item.put("ok", item.optInt("ok", 0) + 1);
                        // 查询类成功只证明“接口活着”，不能代表“完成动作能过”；
                        // 只有动作类成功才把端点标成通过，避免 canary 被列表请求污染。
                        if (!isQueryMethod(key)) {
                            item.put("state", PASS);
                            item.put("strikes", 0);
                            item.put("until", 0L);
                            item.put("reason", "");
                        } else if (item.optString("state", "").isEmpty()) {
                            item.put("reason", "query-only");
                        }
                        break;
                    case C1009:
                    case C1004:
                    case EMPTY:
                    case FAIL:
                    default:
                        item.put("fail", item.optInt("fail", 0) + 1);
                        if (outcome == RpcMetrics.Outcome.C1009) {
                            item.put("c1009", item.optInt("c1009", 0) + 1);
                            item.put("reason", "1009");
                        } else if (outcome == RpcMetrics.Outcome.C1004) {
                            item.put("c1004", item.optInt("c1004", 0) + 1);
                            item.put("reason", "1004");
                        } else if (outcome == RpcMetrics.Outcome.EMPTY) {
                            item.put("reason", "empty");
                        } else {
                            item.put("reason", "fail");
                        }
                        item.put("state", FAIL);
                        item.put("strikes", item.optInt("strikes", 0) + 1);
                        item.put("until", now + FAIL_TTL_MS);
                        break;
                }
                root.put("methods", methods);
                FileUtil.write2File(root.toString(), file());
            } catch (Throwable t) {
                Log.err("EndpointState", "observe err:", t);
            }
        }
    }

    /** 任务端点对应的真实方法今天是否处于失败冷却（匹配不到按未知处理）。 */
    public static boolean isFailingTaskEndpoint(String endpoint) {
        String state = stateOfTaskEndpoint(endpoint);
        return FAIL.equals(state);
    }

    /** 该方法今天是否处于失败冷却。 */
    public static boolean isFailing(String method) {
        JSONObject item = rawState(normalize(method));
        return item != null && FAIL.equals(item.optString("state", ""))
                && item.optLong("until", 0L) > System.currentTimeMillis();
    }

    /**
     * 任务端点 → 端点结论。任务侧端点可能是短名（water/draw）或方法后缀（antfarm.cook），
     * 这里按“后缀/包含”匹配最近一次记录，匹配不到返回未知。
     */
    public static String stateOfTaskEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return "";
        }
        JSONObject best = null;
        long bestAt = -1L;
        synchronized (LOCK) {
            JSONObject methods = loadRoot().optJSONObject("methods");
            if (methods == null) {
                return "";
            }
            Iterator<String> keys = methods.keys();
            while (keys.hasNext()) {
                String method = keys.next();
                if (!endpointMatches(endpoint, method)) {
                    continue;
                }
                JSONObject item = methods.optJSONObject(method);
                if (item == null) {
                    continue;
                }
                long at = item.optLong("lastAt", 0L);
                if (at > bestAt) {
                    bestAt = at;
                    best = item;
                }
            }
        }
        if (best == null) {
            return "";
        }
        return best.optString("state", "");
    }

    /** 手动页顶部一行：今天失败的端点，按失败次数排序。 */
    public static String failingSummary(int limit) {
        List<String> rows = new ArrayList<>();
        synchronized (LOCK) {
            try {
                JSONObject methods = loadRoot().optJSONObject("methods");
                if (methods == null) {
                    return "";
                }
                List<JSONObject> items = new ArrayList<>();
                Iterator<String> keys = methods.keys();
                while (keys.hasNext()) {
                    JSONObject item = methods.optJSONObject(keys.next());
                    if (item != null && FAIL.equals(item.optString("state", ""))) {
                        items.add(item);
                    }
                }
                items.sort((a, b) -> b.optInt("fail", 0) - a.optInt("fail", 0));
                for (JSONObject item : items) {
                    if (rows.size() >= limit) {
                        break;
                    }
                    rows.add(item.optString("method", "") + " 失败" + item.optInt("fail", 0)
                            + (item.optString("reason", "").isEmpty() ? "" : "（" + item.optString("reason", "") + "）"));
                }
            } catch (Throwable ignored) {
            }
        }
        return String.join(" · ", rows);
    }

    public static String stateLabel(String state) {
        if (PASS.equals(state)) {
            return "端点通过";
        }
        if (FAIL.equals(state)) {
            return "端点失败";
        }
        return "";
    }

    private static JSONObject rawState(String method) {
        synchronized (LOCK) {
            try {
                JSONObject methods = loadRoot().optJSONObject("methods");
                return methods == null ? null : methods.optJSONObject(method);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /** 任务侧端点（短名/后缀）与真实 RPC 方法是否同一端点。 */
    public static boolean endpointMatches(String endpoint, String method) {
        if (endpoint == null || method == null || endpoint.isEmpty() || method.isEmpty()) {
            return false;
        }
        String wanted = endpoint.toLowerCase(Locale.ROOT);
        if (wanted.contains(":")) {
            wanted = wanted.substring(0, wanted.indexOf(':'));
        }
        String lowerMethod = method.toLowerCase(Locale.ROOT);
        return simpleName(method).equals(wanted)
                || lowerMethod.endsWith("." + wanted)
                || lowerMethod.contains(wanted);
    }

    /** 列表/查询类方法：成功不代表动作能力，失败仍按失败记录。 */
    private static boolean isQueryMethod(String method) {
        String name = simpleName(method);
        return name.contains("query") || name.contains("list") || name.startsWith("get")
                || name.contains("index") || name.contains("status") || name.contains("home")
                || name.contains("detail") || name.contains("info") || name.contains("sync")
                || name.contains("page") || name.contains("rank") || name.contains("panel")
                || name.contains("probe") || name.contains("config");
    }

    private static String normalize(String method) {
        return method == null ? "" : method.trim();
    }

    private static String simpleName(String method) {
        int cut = method.lastIndexOf('.');
        return cut < 0 ? method.toLowerCase(Locale.ROOT) : method.substring(cut + 1).toLowerCase(Locale.ROOT);
    }
}
