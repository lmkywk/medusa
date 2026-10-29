package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antFarm.AntFarmRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** 爱心鸡结号只读状态：爱心值、我的排名、本周捐蛋数；只缓存，不触发写入。 */
public final class FarmCompetitionStatus {

    private static final String TAG = "FarmCompetitionStatus";

    private FarmCompetitionStatus() {
    }

    public static boolean capture(String userId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            return recordFromResponse(userId, jo);
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    public static boolean recordFromResponse(String userId, JSONObject jo) {
        try {
            if (userId == null || userId.isEmpty() || jo == null) {
                return false;
            }
            int loveValue = jo.optInt("benevolenceScore", -1);
            int rank = 0;
            int myDonation = 0;
            int rank1Donation = 0;
            JSONObject home = jo.optJSONObject("donationRankHomeInfo");
            JSONArray list = home == null ? null : home.optJSONArray("userDonationRankList");
            JSONArray roster = new JSONArray();
            if (list != null) {
                String myId = UserIdMap.getCurrentUid();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject user = list.optJSONObject(i);
                    if (user == null) {
                        continue;
                    }
                    int r = user.optInt("rankOrder", 0);
                    int donation = user.optInt("donationNum", 0);
                    if (r == 1) {
                        rank1Donation = donation;
                    }
                    if (myId != null && myId.equals(user.optString("userId"))) {
                        rank = r;
                        myDonation = donation;
                    }
                    String memberId = user.optString("userId", "");
                    if (!memberId.isEmpty()) {
                        try {
                            JSONObject member = new JSONObject();
                            member.put("id", memberId);
                            member.put("name", UserIdMap.getShowName(memberId));
                            member.put("rank", r);
                            member.put("score", donation);
                            roster.put(member);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            PkRoster.record(userId, PkRoster.DOMAIN_FARM, roster, roster.length() > 0);
            JSONObject out = new JSONObject();
            out.put("loveValue", loveValue);
            out.put("rank", rank);
            out.put("myDonation", myDonation);
            out.put("rank1Donation", rank1Donation);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getFarmCompetitionStatusFile(userId));
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "recordFromResponse err:", t);
            return false;
        }
    }

    /** 只返回今天的数据；跨天不显示旧排名。 */
    public static JSONObject loadToday(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            File file = FileUtil.getFarmCompetitionStatusFile(userId);
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
