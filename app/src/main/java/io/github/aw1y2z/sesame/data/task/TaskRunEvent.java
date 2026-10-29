package io.github.aw1y2z.sesame.data.task;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.aw1y2z.sesame.data.plan.PlanContext;
import io.github.aw1y2z.sesame.data.plan.RunActivity;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RunScope;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 叶子任务事件流：每条 = 一次任务尝试/领奖/跳过，机器可直接消费。
 * 与 events.*.jsonl 分开，避免计划/账本事件淹没任务级数据；AI 维护模块只读这一份。
 */
public final class TaskRunEvent {

    private static final String TAG = "TaskRunEvent";
    private static final long MAX_BYTES = 16L * 1024L * 1024L;
    private static final long RETENTION_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final Object LOCK = new Object();
    private static volatile String lastPruneDay = "";

    private TaskRunEvent() {
    }

    /** TaskAttemptPolicy 的统一出口。 */
    public static void record(String phase, String key, String title,
                              TaskAttemptPolicy.Site site, TaskAttemptPolicy.Outcome outcome,
                              long durationMs, String reason) {
        try {
            String target = safe(PlanContext.get());
            if (target.isEmpty()) {
                target = safe(RunScope.bound());
            }
            String domain = domainOf(target);
            String mechanism = "";
            String endpoint = "";
            try {
                if (site != null) {
                    endpoint = safe(site.listField);
                    TaskCapabilityRegistry.Spec spec = TaskCapabilityRegistry.resolve(
                            domain, safe(key), safe(title), "", 0, 0, safe(site.taskSceneCode));
                    mechanism = spec == null ? "" : spec.mechanism.name();
                }
            } catch (Throwable ignored) {
            }
            write(phase, target, key, title, domain, mechanism, endpoint,
                    outcome == null ? "" : outcome.name(), durationMs, reason);
        } catch (Throwable ignored) {
        }
    }

    /** 按类过滤/端点冷却等未真正发起请求的跳过。 */
    public static void skip(String domain, String taskId, String title, String reason) {
        try {
            String mechanism = "";
            String endpoint = "";
            try {
                TaskCapabilityRegistry.Spec spec = TaskCapabilityRegistry.resolve(
                        domain, safe(taskId), safe(title), "", 0, 0, "");
                mechanism = spec == null ? "" : spec.mechanism.name();
                endpoint = spec == null ? "" : spec.endpoint;
            } catch (Throwable ignored) {
            }
            String target = safe(PlanContext.get());
            if (target.isEmpty()) {
                target = safe(domain);
            }
            write("skip", target, taskId, title, safe(domain), mechanism, endpoint,
                    "SKIPPED", 0L, reason);
        } catch (Throwable ignored) {
        }
    }

    /** 领奖复核出口（TaskAward 没有 site，只记结果与定位键）。 */
    public static void claim(String key, String title, boolean received, String reason) {
        try {
            String target = safe(PlanContext.get());
            if (target.isEmpty()) {
                target = safe(RunScope.bound());
            }
            write("claim", target, key, title, domainOf(target), "", "",
                    received ? "AWARDED" : "FAILED", 0L, reason);
        } catch (Throwable ignored) {
        }
    }

    private static void write(String phase, String target, String taskId, String title, String domain,
                              String mechanism, String endpoint, String outcome,
                              long durationMs, String reason) {
        try {
        long now = System.currentTimeMillis();
        JSONObject item = new JSONObject();
        item.put("ts", now);
        item.put("at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
                .format(new Date(now)));
        String uid = safe(RunActivity.currentUserId());
        if (uid.isEmpty()) {
            uid = safe(UserIdMap.getCurrentUid());
        }
        item.put("uid", uid);
        String runId = safe(RunActivity.currentRunId());
        item.put("runId", runId);
        String planId = runId.contains("@") ? runId.substring(0, runId.indexOf('@')) : runId;
        item.put("planId", planId);
        item.put("target", safe(target));
        item.put("domain", safe(domain));
        item.put("phase", safe(phase));
        item.put("taskId", safe(taskId));
        item.put("title", safe(title));
        item.put("mechanism", safe(mechanism));
        item.put("endpoint", safe(endpoint));
        item.put("outcome", safe(outcome));
        item.put("durationMs", Math.max(0L, durationMs));
        item.put("reason", safe(reason));
        try {
            item.put("moduleVersion", io.github.aw1y2z.sesame.BuildConfig.VERSION_NAME);
        } catch (Throwable ignored) {
        }
        append(item.toString());
        } catch (Throwable t) {
            Log.debug(TAG + " 组装事件失败：" + t);
        }
    }

    private static void append(String line) {
        synchronized (LOCK) {
            try {
                File dir = FileUtil.LOG_DIRECTORY_FILE;
                if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
                    return;
                }
                String day = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
                prune(dir, day);
                File file = new File(dir, "tasks." + day + ".jsonl");
                if (file.length() >= MAX_BYTES) {
                    return;
                }
                FileOutputStream out = new FileOutputStream(file, true);
                try {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } finally {
                    try {
                        out.close();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                Log.debug(TAG + " 写入失败：" + t);
            }
        }
    }

    private static void prune(File dir, String day) {
        if (day.equals(lastPruneDay)) {
            return;
        }
        lastPruneDay = day;
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.startsWith("tasks.") && name.endsWith(".jsonl")
                    && file.lastModified() < cutoff) {
                file.delete();
            }
        }
    }

    private static String domainOf(String target) {
        String value = safe(target);
        int dot = value.indexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
