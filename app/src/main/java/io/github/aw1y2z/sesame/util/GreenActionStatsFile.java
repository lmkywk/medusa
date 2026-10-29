package io.github.aw1y2z.sesame.util;

import org.json.JSONObject;

import java.io.File;

/**
 * 绿色行动四项统计的跨进程落盘：注入进程写，模块 UI 读。
 * 字段来自 queryHomePage -> data.userInfoVO：
 * dailyActionNum / totalActionDay / totalActionNum / totalMedalNum。
 */
public final class GreenActionStatsFile {

    public static final String FILE_NAME = "green_action_stats.json";
    private static final String TAG = "GreenActionStatsFile";

    private GreenActionStatsFile() {}

    public static void saveFromResponse(String response) {
        try {
            saveFromData(new JSONObject(response).optJSONObject("data"));
        } catch (Throwable th) {
            Log.err(TAG, "saveFromResponse err:", th);
        }
    }

    public static void saveFromData(JSONObject data) {
        if (data == null) {
            return;
        }
        try {
            JSONObject userInfo = data.optJSONObject("userInfoVO");
            if (userInfo == null) {
                return;
            }
            JSONObject stats = new JSONObject();
            stats.put("updatedAt", System.currentTimeMillis());
            stats.put("todayDone", userInfo.optInt("dailyActionNum", 0));
            // 服务端响应没有目标值；绿色行动首页固定显示 5 次目标
            stats.put("todayGoal", 5);
            stats.put("streakDays", userInfo.optInt("totalActionDay", 0));
            stats.put("totalCheckins", userInfo.optInt("totalActionNum", 0));
            stats.put("medals", userInfo.optInt("totalMedalNum", 0));
            FileUtil.write2File(
                    stats.toString(),
                    new File(FileUtil.MAIN_DIRECTORY_FILE, FILE_NAME)
            );
        } catch (Throwable th) {
            Log.err(TAG, "saveFromData err:", th);
        }
    }
}
