package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 森林只读总览：当前能量、今日收入/被收、好友可偷/礼盒/保护。 */
public final class ForestStatus {

    private static final String TAG = "ForestStatus";

    private ForestStatus() {
    }

    public static boolean capture(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            JSONObject home = new JSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, home)) {
                return false;
            }
            int currentEnergy = 0;
            int vitality = 0;
            JSONObject base = home.optJSONObject("userBaseInfo");
            if (base != null) {
                currentEnergy = base.optInt("currentEnergy", 0);
            }
            JSONObject vitalityInfo = home.optJSONObject("userVitalityInfo");
            if (vitalityInfo != null) {
                vitality = vitalityInfo.optInt("totalVitalityAmount", 0);
            }
            int bubbles = home.optJSONArray("bubbles") == null ? 0 : home.optJSONArray("bubbles").length();

            int todayObtained = 0;
            int todayRobbed = 0;
            try {
                JSONObject dynamics = new JSONObject(AntForestRpcCall.queryDynamicsIndex());
                JSONObject summary = dynamics.optJSONObject("todayEnergySummary");
                if (summary != null) {
                    todayObtained = summary.optInt("obtainTotal", 0);
                    todayRobbed = summary.optInt("robbedTotal", 0);
                }
            } catch (Throwable ignored) {
            }

            int canCollectFriends = 0;
            int giftBoxFriends = 0;
            int protectFriends = 0;
            try {
                JSONObject ranking = new JSONObject(AntForestRpcCall.queryEnergyRanking());
                JSONArray friends = ranking.optJSONArray("friendRanking");
                if (friends != null) {
                    for (int i = 0; i < friends.length(); i++) {
                        JSONObject friend = friends.optJSONObject(i);
                        if (friend == null) {
                            continue;
                        }
                        if (friend.optBoolean("canCollectEnergy", false)) {
                            canCollectFriends++;
                        }
                        if (friend.optBoolean("canCollectGiftBox", false)) {
                            giftBoxFriends++;
                        }
                        if (friend.optBoolean("canProtectBubble", false)) {
                            protectFriends++;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            JSONObject out = new JSONObject();
            out.put("currentEnergy", currentEnergy);
            out.put("vitality", vitality);
            out.put("bubbles", bubbles);
            out.put("todayObtained", todayObtained);
            out.put("todayRobbed", todayRobbed);
            out.put("canCollectFriends", canCollectFriends);
            out.put("giftBoxFriends", giftBoxFriends);
            out.put("protectFriends", protectFriends);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getForestStatusFile(userId));
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
            File file = FileUtil.getForestStatusFile(userId);
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
