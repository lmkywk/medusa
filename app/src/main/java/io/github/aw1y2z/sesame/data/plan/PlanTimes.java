package io.github.aw1y2z.sesame.data.plan;

import java.util.Calendar;
import java.util.List;

/**
 * 4.0.0：计划时间计算的纯函数实现，供 PlanScheduler 与 golden test 共用。
 * <p>原逻辑内嵌在 PlanScheduler 里、依赖 Calendar.getInstance() 默认时区，无法单测；这里原样搬出来。
 */
public final class PlanTimes {

    private PlanTimes() {
    }

    /** "HH:mm" → 今天该时刻的时间戳（秒/毫秒清零）。 */
    public static long todayAt(String time, Calendar now) {
        Calendar calendar = (Calendar) now.clone();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time.substring(0, 2)));
        calendar.set(Calendar.MINUTE, Integer.parseInt(time.substring(3, 5)));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static long todayAt(String time, long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        return todayAt(time, calendar);
    }

    public static String formatTime(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        return String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    /**
     * 今天该跑但还没跑的那一轮：窗口内 + 轮次时间已过 + 今天没尝试过。
     * 窗口外返回 null（不补跑）。
     */
    public static String firstDueTime(Plan plan, long now, List<PlanHistory.Record> history) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        long start = todayAt(plan.getWindowStart(), calendar);
        long end = todayAt(plan.getWindowEnd(), calendar);
        if (now < start || now > end) {
            return null;
        }
        for (String time : plan.getRoundTimes()) {
            long at = todayAt(time, calendar);
            if (at <= now && !isAttempted(history, plan, time, now)) {
                return time;
            }
        }
        return null;
    }

    /** 今天是否尝试过（被跳过的轮次不算，否则一次“条件不满足”会把整轮吃掉）。 */
    public static boolean isAttempted(List<PlanHistory.Record> history, Plan plan, String time, long now) {
        return hasRecord(history, plan, time, now, false);
    }

    /** 今天是否完成过。 */
    public static boolean isCompleted(List<PlanHistory.Record> history, Plan plan, String time, long now) {
        return hasRecord(history, plan, time, now, true);
    }

    private static boolean hasRecord(List<PlanHistory.Record> history, Plan plan, String time, long now,
                                     boolean requireCompleted) {
        if (history == null) {
            return false;
        }
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        int year = today.get(Calendar.YEAR);
        int day = today.get(Calendar.DAY_OF_YEAR);
        for (PlanHistory.Record record : history) {
            if (!plan.getId().equals(record.planId) || !time.equals(record.roundTime)) {
                continue;
            }
            if (PlanHistory.SKIPPED.equals(record.status)) {
                continue;
            }
            Calendar recordDay = Calendar.getInstance();
            recordDay.setTimeInMillis(record.startedAt);
            boolean sameDay = recordDay.get(Calendar.YEAR) == year
                    && recordDay.get(Calendar.DAY_OF_YEAR) == day;
            if (sameDay && (!requireCompleted || PlanHistory.COMPLETED.equals(record.status))) {
                return true;
            }
        }
        return false;
    }
}