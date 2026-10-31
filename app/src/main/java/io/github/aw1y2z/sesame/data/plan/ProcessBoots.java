package io.github.aw1y2z.sesame.data.plan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 进程启动记账：每次模块装载写一条时间戳，只留最近 24 小时。
 * <p>账本用它把一段计划的墙钟时长拆成「真实工作」和「死时间」：
 * 区间内每多一次启动，就说明中途进程死过一次，那之前的工作是被截断的。
 * 记账失败（多进程并发写、文件损坏）只影响统计，不影响执行。
 */
public final class ProcessBoots {

    private static final int KEEP = 400;
    private static final long WINDOW_MS = 24 * 3600_000L;

    private ProcessBoots() {
    }

    /** 模块每次装载调用一次 */
    public static void record(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        try {
            File file = file(userId);
            long now = System.currentTimeMillis();
            JSONArray kept = new JSONArray();
            JSONArray old = read(file);
            for (int i = 0; i < old.length(); i++) {
                long t = old.optLong(i, 0);
                if (t > 0 && now - t <= WINDOW_MS) {
                    kept.put(t);
                }
            }
            kept.put(now);
            while (kept.length() > KEEP) {
                kept.remove(0);
            }
            JSONObject root = new JSONObject();
            root.put("boots", kept);
            FileUtil.write2File(root.toString(), file);
        } catch (Throwable t) {
            Log.debug("进程启动记账失败：" + t);
        }
    }

    /** [from, to] 内的启动次数；首尾都算 */
    public static int countBetween(String userId, long from, long to) {
        if (userId == null || userId.isEmpty() || to <= from) {
            return 0;
        }
        try {
            JSONArray array = read(file(userId));
            int count = 0;
            for (int i = 0; i < array.length(); i++) {
                long t = array.optLong(i, 0);
                if (t >= from && t <= to) {
                    count++;
                }
            }
            return count;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static File file(String userId) {
        return new File(FileUtil.getUserConfigDirectoryFile(userId), "process_boots.json");
    }

    private static JSONArray read(File file) {
        try {
            if (!file.exists()) {
                return new JSONArray();
            }
            String text = FileUtil.readFromFile(file);
            if (text == null || text.isEmpty()) {
                return new JSONArray();
            }
            JSONArray array = new JSONObject(text).optJSONArray("boots");
            return array == null ? new JSONArray() : array;
        } catch (Throwable t) {
            return new JSONArray();
        }
    }
}