package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** 最近一次海洋迎回记录；迎回卡只显示名称，不存海域/收集明细。 */
public final class OceanWelcomeStatus {

    private static final String TAG = "OceanWelcomeStatus";

    private OceanWelcomeStatus() {
    }

    public static void record(String name) {
        try {
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            String userId = UserIdMap.getCurrentUid();
            if (userId == null || userId.isEmpty()) {
                return;
            }
            JSONObject jo = new JSONObject();
            jo.put("name", name.trim());
            jo.put("at", System.currentTimeMillis());
            FileUtil.write2File(jo.toString(), FileUtil.getOceanWelcomeStatusFile(userId));
        } catch (Throwable t) {
            Log.err(TAG, "record err:", t);
        }
    }

    /** 只返回今天的最近迎回名称；跨天不显示旧名称。 */
    public static String loadTodayName(String userId) {
        try {
            File file = FileUtil.getOceanWelcomeStatusFile(userId);
            if (!file.exists()) {
                return "";
            }
            String json = FileUtil.readFromFile(file);
            if (json == null || json.trim().isEmpty()) {
                return "";
            }
            JSONObject jo = new JSONObject(json);
            long at = jo.optLong("at", 0L);
            if (at <= 0 || !TaskLedgerStore.today().equals(TaskLedgerStore.dayOf(at))) {
                return "";
            }
            return jo.optString("name", "");
        } catch (Throwable t) {
            Log.err(TAG, "loadTodayName err:", t);
            return "";
        }
    }
}
