package io.github.aw1y2z.sesame.data.ocean;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Status;

/**
 * 海洋好友清理配额。
 * <p>服务端按账号限每日 20 次好友海域清理（2026-10-25 日志：canClearFriendNumLimit=20，
 * 超限返回 HELP_CLEAN_LIMIT / HELP_CLEAN_ALL_FRIEND_LIMIT）。自己海域清理不占这 20 次。
 * <p>窗口规则：只开上午或只开下午时该窗口 20 次；两个窗口都开则各 10 次。
 */
public final class OceanCleanQuota {

    public static final int DAILY_LIMIT = 20;
    public static final int BOTH_WINDOW_QUOTA = 10;
    public static final int SINGLE_WINDOW_QUOTA = 20;
    public static final String STATE_FILE = "ocean_clean_state.json";

    private static final String TAG_DAILY = "ocean::cleanFriendDaily";
    private static final String TAG_MORNING = "ocean::cleanFriendMorning";
    private static final String TAG_EVENING = "ocean::cleanFriendEvening";

    private OceanCleanQuota() {
    }

    public static int quotaFor(boolean morning, boolean morningOn, boolean eveningOn) {
        boolean windowOn = morning ? morningOn : eveningOn;
        if (!windowOn) {
            return 0;
        }
        return morningOn && eveningOn ? BOTH_WINDOW_QUOTA : SINGLE_WINDOW_QUOTA;
    }

    public static int dailyUsed() {
        return Status.getIntFlagToday(TAG_DAILY);
    }

    public static int windowUsed(boolean morning) {
        return Status.getIntFlagToday(morning ? TAG_MORNING : TAG_EVENING);
    }

    /** 当前窗口还能清几个：同时受窗口配额和每日 20 次限制。 */
    public static int remaining(boolean morning, boolean morningOn, boolean eveningOn) {
        int quota = quotaFor(morning, morningOn, eveningOn);
        if (quota <= 0) {
            return 0;
        }
        int byWindow = quota - windowUsed(morning);
        int byDay = DAILY_LIMIT - dailyUsed();
        return Math.max(0, Math.min(byWindow, byDay));
    }

    public static void recordSuccess(boolean morning, boolean morningOn, boolean eveningOn) {
        int daily = Math.min(DAILY_LIMIT, dailyUsed() + 1);
        Status.setIntFlagToday(TAG_DAILY, daily);
        if (morning) {
            Status.setIntFlagToday(TAG_MORNING, windowUsed(true) + 1);
        } else {
            Status.setIntFlagToday(TAG_EVENING, windowUsed(false) + 1);
        }
        write(morningOn, eveningOn, false);
    }

    /** 服务端明确返回“今日好友清理已达上限”时，把本地计数对齐到 20，避免继续试探。 */
    public static void markServerLimit(boolean morningOn, boolean eveningOn) {
        Status.setIntFlagToday(TAG_DAILY, DAILY_LIMIT);
        write(morningOn, eveningOn, true);
    }

    public static JSONObject read() {
        try {
            File file = new File(FileUtil.MAIN_DIRECTORY_FILE, STATE_FILE);
            if (!file.exists()) {
                return null;
            }
            return new JSONObject(FileUtil.readFromFile(file));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void write(boolean morningOn, boolean eveningOn, boolean serverLimited) {
        try {
            JSONObject jo = new JSONObject();
            jo.put("day", today());
            jo.put("dailyUsed", dailyUsed());
            jo.put("morningUsed", windowUsed(true));
            jo.put("eveningUsed", windowUsed(false));
            jo.put("dailyLimit", DAILY_LIMIT);
            jo.put("morningQuota", quotaFor(true, morningOn, eveningOn));
            jo.put("eveningQuota", quotaFor(false, morningOn, eveningOn));
            jo.put("morningOn", morningOn);
            jo.put("eveningOn", eveningOn);
            jo.put("serverLimited", serverLimited);
            jo.put("updatedAt", System.currentTimeMillis());
            FileUtil.write2File(jo.toString(), new File(FileUtil.MAIN_DIRECTORY_FILE, STATE_FILE));
        } catch (Throwable ignored) {
        }
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }
}
