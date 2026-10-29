package io.github.aw1y2z.sesame.util;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 结构化事件日志：JSONL，按天一个文件，供排障和状态页消费。
 * 只写 ID/状态/原因码，不写 token、好友列表、任务标题原文。
 */
public final class EventLog {

    private static final String TAG = "EventLog";
    private static final long MAX_BYTES = 16L * 1024L * 1024L;
    private static final long RETENTION_MS = 3L * 24L * 60L * 60L * 1000L;

    private static final Object LOCK = new Object();
    private static volatile boolean writeFailed = false;
    private static volatile String lastPruneDay = "";

    private EventLog() {
    }

    public static void scheduler(String event, Object... kv) {
        event("scheduler", event, kv);
    }

    public static void plan(String event, Object... kv) {
        event("plan", event, kv);
    }

    public static void risk(String event, Object... kv) {
        event("risk", event, kv);
    }

    public static void ledger(String event, Object... kv) {
        event("ledger", event, kv);
    }

    public static void system(String event, Object... kv) {
        event("system", event, kv);
    }

    /** kv 为交替的 key/value；value 为 null 时跳过。事件写入失败绝不影响调用方。 */
    public static void event(String category, String event, Object... kv) {
        try {
            long now = System.currentTimeMillis();
            JSONObject item = new JSONObject();
            item.put("ts", now);
            item.put("at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
                    .format(new Date(now)));
            item.put("pid", android.os.Process.myPid());
            item.put("category", category == null ? "" : category);
            item.put("event", event == null ? "" : event);
            String uid = null;
            try {
                uid = UserIdMap.getCurrentUid();
            } catch (Throwable ignored) {
            }
            if (uid != null && !uid.isEmpty()) {
                item.put("uid", uid);
            }
            for (int i = 0; i + 1 < kv.length; i += 2) {
                if (kv[i] == null) {
                    continue;
                }
                Object value = kv[i + 1];
                if (value != null) {
                    item.put(String.valueOf(kv[i]), value);
                }
            }
            append(item.toString());
        } catch (Throwable ignored) {
        }
    }

    private static void append(String line) {
        synchronized (LOCK) {
            try {
                File dir = FileUtil.LOG_DIRECTORY_FILE;
                if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
                    return;
                }
                String day = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                        .format(new Date());
                prune(dir, day);
                File file = new File(dir, "events." + day + ".jsonl");
                if (file.length() >= MAX_BYTES) {
                    return;
                }
                FileOutputStream out = new FileOutputStream(file, true);
                try {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    writeFailed = false;
                } finally {
                    try {
                        out.close();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                if (!writeFailed) {
                    writeFailed = true;
                    Log.debug("事件日志写入失败：" + t);
                }
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
            if (name.startsWith("events.") && name.endsWith(".jsonl")
                    && file.lastModified() < cutoff) {
                file.delete();
            }
        }
    }
}
