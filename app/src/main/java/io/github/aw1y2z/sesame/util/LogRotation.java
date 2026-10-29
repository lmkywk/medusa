package io.github.aw1y2z.sesame.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 日志轮转：保留 3 天、总量上限 300MB。
 * <p>抓包日志一天能到几十 MB，不清理会撑爆手机；只删日志目录里的普通文件，不碰配置与备份。
 */
public final class LogRotation {

    private static final long MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000;
    private static final long MAX_TOTAL_BYTES = 300L * 1024 * 1024;

    private LogRotation() {
    }

    public static synchronized void run() {
        try {
            File dir = FileUtil.LOG_DIRECTORY_FILE;
            File[] files = dir != null ? dir.listFiles() : null;
            if (files == null || files.length == 0) {
                return;
            }
            long now = System.currentTimeMillis();
            List<File> alive = new ArrayList<>();
            long total = 0;
            for (File f : files) {
                if (f == null || !f.isFile()) {
                    continue;
                }
                if (now - f.lastModified() > MAX_AGE_MS) {
                    if (f.delete()) {
                        Log.i("日志轮转：删除过期 " + f.getName());
                    }
                    continue;
                }
                alive.add(f);
                total += f.length();
            }
            if (total <= MAX_TOTAL_BYTES) {
                return;
            }
            alive.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : alive) {
                if (total <= MAX_TOTAL_BYTES) {
                    break;
                }
                long len = f.length();
                if (f.delete()) {
                    total -= len;
                    Log.i("日志轮转：超限删除 " + f.getName());
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
    }
}
