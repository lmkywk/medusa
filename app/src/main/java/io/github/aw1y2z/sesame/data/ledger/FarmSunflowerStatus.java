package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antFarm.AntFarmRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 加油小葵公益状态：葵花籽余额、项目/房间、本月已助份额；只读缓存。 */
public final class FarmSunflowerStatus {

    public static final String DEFAULT_PROJECT_ID = "303";
    private static final String TAG = "FarmSunflowerStatus";

    private FarmSunflowerStatus() {
    }

    /** 00:01 建账和卡片刷新共用：只查项目状态，不捐。 */
    public static JSONObject capture(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            JSONObject cached = loadToday(userId);
            String projectId = cached == null ? DEFAULT_PROJECT_ID : cached.optString("projectId", DEFAULT_PROJECT_ID);
            String targetId = cached == null ? "" : cached.optString("targetId", "");
            JSONObject jo = query(projectId, targetId);
            if (jo == null && !targetId.isEmpty()) {
                jo = query(projectId, "");
            }
            if (jo == null) {
                return null;
            }
            return parseAndSave(userId, jo);
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return null;
        }
    }

    private static JSONObject query(String projectId, String targetId) {
        try {
            String json = AntFarmRpcCall.getSoldByProjectInfo(projectId, targetId);
            JSONObject jo = new JSONObject(json);
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return null;
            }
            return jo;
        } catch (Throwable t) {
            Log.err(TAG, "query err:", t);
            return null;
        }
    }

    public static JSONObject parseAndSave(String userId, JSONObject jo) {
        try {
            JSONObject activity = jo.optJSONObject("activityInfo");
            if (activity == null) {
                return null;
            }
            String projectId = activity.optString("projectId", DEFAULT_PROJECT_ID);
            String activityId = activity.optString("activityId", "");
            String period = activity.optString("activityPeriod", "");

            String batchId = "";
            String batchName = "";
            String targetId = "";
            String targetName = "";
            JSONArray batches = activity.optJSONArray("batchInfo");
            if (batches != null && batches.length() > 0) {
                JSONObject batch = batches.optJSONObject(0);
                if (batch != null) {
                    batchId = batch.optString("batchId", "");
                    batchName = batch.optString("batchName", "");
                    JSONArray targets = batch.optJSONArray("targetList");
                    if (targets != null) {
                        JSONObject fallback = null;
                        for (int i = 0; i < targets.length(); i++) {
                            JSONObject target = targets.optJSONObject(i);
                            if (target == null) {
                                continue;
                            }
                            if (fallback == null) {
                                fallback = target;
                            }
                            if (target.optBoolean("acceptBusinessCoin", false) && !target.optBoolean("finished", false)) {
                                fallback = target;
                                break;
                            }
                        }
                        if (fallback != null) {
                            targetId = fallback.optString("targetId", "");
                            targetName = fallback.optString("name", "");
                        }
                    }
                }
            }

            int seeds = 0;
            int eggs = 0;
            JSONArray assets = jo.optJSONArray("assetInfoList");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) {
                        continue;
                    }
                    String type = asset.optString("assetType", "");
                    if ("KUIHUA_COIN".equals(type)) {
                        seeds = asset.optInt("assetAmount", 0);
                    } else if ("BENE_SCORE".equals(type)) {
                        eggs = asset.optInt("assetAmount", 0);
                    }
                }
            }

            int myDonation = 0;
            int rankPercent = 0;
            JSONObject rankInfo = jo.optJSONObject("rankInfoVO");
            if (rankInfo != null) {
                JSONObject mine = rankInfo.optJSONObject("myRankItem");
                if (mine != null) {
                    myDonation = optInt(mine.opt("donateAmount"));
                    rankPercent = mine.optInt("rankStageOverPercent", 0);
                }
            }

            JSONObject project = jo.optJSONObject("projectInfo");
            String projectName = project == null ? "" : project.optString("projectName", "");

            JSONObject out = new JSONObject();
            out.put("projectId", projectId);
            out.put("projectName", projectName);
            out.put("activityId", activityId);
            out.put("activityPeriod", period);
            out.put("batchId", batchId);
            out.put("batchName", batchName);
            out.put("targetId", targetId);
            out.put("targetName", targetName);
            out.put("seeds", seeds);
            out.put("eggs", eggs);
            out.put("ratio", Math.max(1, (int) jo.optDouble("changeOneEggNeedBusinessCoinAmount", 50)));
            out.put("myDonation", myDonation);
            out.put("rankPercent", rankPercent);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getFarmSunflowerStatusFile(userId));
            return out;
        } catch (Throwable t) {
            Log.err(TAG, "parseAndSave err:", t);
            return null;
        }
    }

    private static int optInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 只返回今天的数据；跨天不显示旧余额。 */
    public static JSONObject loadToday(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            File file = FileUtil.getFarmSunflowerStatusFile(userId);
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
