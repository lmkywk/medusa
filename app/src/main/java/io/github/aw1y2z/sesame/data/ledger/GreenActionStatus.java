package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antForest.EcoLifeRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 绿色行动只读总览：今日打卡、分类完成、坚持天数、累计打卡、勋章。 */
public final class GreenActionStatus {

    private static final String TAG = "GreenActionStatus";

    private GreenActionStatus() {
    }

    public static boolean capture(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            JSONObject jo = new JSONObject(EcoLifeRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null) {
                return false;
            }

            JSONArray categories = new JSONArray();
            int actionTotal = 0;
            int actionDone = 0;
            JSONArray categoryList = data.optJSONArray("actionListVO");
            if (categoryList != null) {
                for (int i = 0; i < categoryList.length(); i++) {
                    JSONObject category = categoryList.optJSONObject(i);
                    if (category == null) {
                        continue;
                    }
                    JSONArray items = category.optJSONArray("actionItemList");
                    int total = items == null ? 0 : items.length();
                    int done = 0;
                    if (items != null) {
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.optJSONObject(j);
                            if (item != null && item.optBoolean("actionStatus", false)) {
                                done++;
                            }
                        }
                    }
                    actionTotal += total;
                    actionDone += done;
                    JSONObject entry = new JSONObject();
                    entry.put("type", category.optString("actionType"));
                    entry.put("name", category.optString("actionTypeName"));
                    entry.put("done", done);
                    entry.put("total", total);
                    categories.put(entry);
                }
            }

            JSONObject userInfo = data.optJSONObject("userInfoVO");
            int todayDone = userInfo == null
                    ? actionDone
                    : userInfo.optInt("dailyActionNum", actionDone);

            JSONObject out = new JSONObject();
            out.put("openStatus", data.optBoolean("openStatus", false));
            out.put("dayPoint", data.optString("dayPoint", ""));
            out.put("todayDone", todayDone);
            out.put("todayActions", actionTotal);
            out.put("todayGoal", 5);
            out.put("streakDays", userInfo == null ? 0 : userInfo.optInt("totalActionDay", 0));
            out.put("totalCheckins", userInfo == null ? 0 : userInfo.optInt("totalActionNum", 0));
            out.put("medals", userInfo == null ? 0 : userInfo.optInt("totalMedalNum", 0));
            out.put("categories", categories);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getGreenActionStatusFile(userId));
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    public static JSONObject loadToday(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            File file = FileUtil.getGreenActionStatusFile(userId);
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
