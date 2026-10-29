package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.goldenbeans.goldenbeansRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RiskGuard;

/** 金豆夺宝只读总览：金豆罐、签到、待领金豆、乐园次数、肥料换豆额度。 */
public final class GoldenBeansStatus {

    private static final String TAG = "GoldenBeansStatus";

    private GoldenBeansStatus() {
    }

    /** 返回主页原始响应，供账本复用，避免一次刷新重复请求。 */
    public static JSONObject fetch() {
        try {
            JSONObject jo = new JSONObject(goldenbeansRpcCall.home());
            if (!jo.optBoolean("success", false)) {
                return null;
            }
            return jo;
        } catch (Throwable t) {
            Log.err(TAG, "fetch err:", t);
            return null;
        }
    }

    public static boolean capture(String userId, JSONObject home) {
        try {
            if (userId == null || userId.isEmpty() || home == null) {
                return false;
            }
            JSONObject jar = home.optJSONObject("jarInfo");
            JSONObject sign = home.optJSONObject("signInfo");
            JSONObject sprout = home.optJSONObject("sproutInfo");
            JSONObject manure = home.optJSONObject("manureExchangeInfo");

            int balance = jar == null ? 0 : jar.optInt("currentProgress", 0);
            int capacity = jar == null ? 0 : jar.optInt("jarCapacity", 0);
            int jars = jar == null ? 0 : jar.optInt("jarCount", 0);

            boolean signed = false;
            int continuous = sign == null ? 0 : sign.optInt("continuousDays", 0);
            int signAward = 0;
            JSONArray signs = sign == null ? null : sign.optJSONArray("signList");
            if (signs != null) {
                for (int i = 0; i < signs.length(); i++) {
                    JSONObject item = signs.optJSONObject(i);
                    if (item != null && item.optBoolean("today", false)) {
                        signed = item.optBoolean("signed", false);
                        signAward = item.optInt("awardCount", 0);
                        break;
                    }
                }
            }

            int pendingBeans = sprout == null ? 0 : sprout.optInt("pendingBeans", 0);
            int drawRights = home.optInt("gameCenterDrawRightsCount", 0);
            boolean showGame = home.optBoolean("showGameCenter", false);
            int taskAward = home.optInt("taskAwardToReceive", 0);
            int taskCount = home.optJSONArray("taskList") == null ? 0 : home.optJSONArray("taskList").length();
            int farmTaskCount = home.optJSONArray("farmTaskList") == null ? 0 : home.optJSONArray("farmTaskList").length();

            JSONObject out = new JSONObject();
            out.put("balance", balance);
            out.put("capacity", capacity);
            out.put("jars", jars);
            out.put("signed", signed);
            out.put("continuous", continuous);
            out.put("signAward", signAward);
            out.put("pendingBeans", pendingBeans);
            out.put("drawRights", drawRights);
            out.put("showGame", showGame);
            out.put("taskAward", taskAward);
            out.put("taskCount", taskCount);
            out.put("farmTaskCount", farmTaskCount);
            if (manure != null) {
                out.put("manureCurrent", manure.optInt("currentManure", 0));
                out.put("manureRemain", manure.optInt("remainQuota", 0));
                out.put("manureMin", manure.optInt("minExchangeAmount", 0));
            }
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getGoldenBeansStatusFile(userId));
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    public static boolean capture(String userId) {
        if (RiskGuard.isPaused("golden")) {
            Log.record("任务账本⏸金豆域保护性暂停，跳过主页抓取");
            return false;
        }
        return capture(userId, fetch());
    }

    public static JSONObject loadToday(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            File file = FileUtil.getGoldenBeansStatusFile(userId);
            if (!file.exists()) {
                return null;
            }
            String json = FileUtil.readFromFile(file);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            JSONObject jo = new JSONObject(json);
            long at = jo.optLong("at", 0L);
            if (at <= 0 || !TaskLedgerStore.today().equals(TaskLedgerStore.dayOf(at))) {
                return null;
            }
            return jo;
        } catch (Throwable t) {
            Log.err(TAG, "loadToday err:", t);
            return null;
        }
    }
}
