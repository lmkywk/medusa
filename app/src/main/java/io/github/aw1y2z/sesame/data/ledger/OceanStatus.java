package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antOcean.AntOceanRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;

/**
 * 神奇海洋只读状态快照：迎回、拼图、摸鱼、自家垃圾。
 * <p>只解析 RPC 响应，不领取、不合成、不清理、不修复；供首页和域页读取。
 */
public final class OceanStatus {

    private static final String TAG = "OceanStatus";

    private OceanStatus() {
    }

    public static boolean capture(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        if (RiskGuard.isVerificationRequired() || RiskGuard.isGlobalPaused()) {
            Log.record("海洋状态⏸风控暂停，跳过只读抓取");
            return false;
        }
        try {
            JSONObject out = new JSONObject();
            out.put("schemaVersion", 1);
            boolean any = false;

            JSONObject home = request(AntOceanRpcCall.queryHomePage(), "queryHomePage");
            if (home != null) {
                any = true;
                JSONObject userInfo = home.optJSONObject("userInfoVO");
                out.put("rubbishNumber", userInfo == null ? -1 : userInfo.optInt("rubbishNumber", -1));
                out.put("canCleanLaterTime", userInfo == null ? 0L : userInfo.optLong("canCleanLaterTime", 0L));
                JSONObject sea = home.optJSONObject("displaySeaAreaVO");
                out.put("currentSeaAreaCode", sea == null ? "" : sea.optString("code", ""));
                out.put("currentChapterName", sea == null ? "" : sea.optString("chapterName", ""));
            }

            JSONObject misc = request(AntOceanRpcCall.queryMiscInfo(), "queryMiscInfo");
            if (misc != null) {
                JSONObject map = misc.optJSONObject("miscHandlerVOMap");
                JSONObject tips = map == null ? null : map.optJSONObject("HOME_TIPS_REFRESH");
                out.put("fishCanBeCombined", tips != null && tips.optBoolean("fishCanBeCombined", false));
                out.put("canBeRepaired", tips != null && tips.optBoolean("canBeRepaired", false));
            }

            JSONObject detail = request(AntOceanRpcCall.querySeaAreaDetailList(), "querySeaAreaDetailList");
            int readyFish = -1;
            int seaAreaNum = -1;
            int fixedSeaAreaNum = -1;
            if (detail != null) {
                seaAreaNum = detail.optInt("seaAreaNum", -1);
                fixedSeaAreaNum = detail.optInt("fixSeaAreaNum", -1);
                out.put("seaAreaNum", seaAreaNum);
                out.put("fixedSeaAreaNum", fixedSeaAreaNum);
                out.put("currentSeaAreaIndex", detail.optInt("currentSeaAreaIndex", -1));
                JSONArray areas = detail.optJSONArray("seaAreaVOs");
                readyFish = 0;
                if (areas != null) {
                    for (int i = 0; i < areas.length(); i++) {
                        JSONObject area = areas.optJSONObject(i);
                        if (area == null) {
                            continue;
                        }
                        readyFish += countReadyFish(area.optJSONArray("fishVO"));
                        JSONObject extra = area.optJSONObject("seaAreaExtraCollectVO");
                        if (extra != null && !"FINISHED".equals(extra.optString("status", ""))) {
                            readyFish += countReadyFish(extra.optJSONArray("fishVO"));
                        }
                    }
                    if (areas.length() > 0) {
                        JSONObject last = areas.optJSONObject(areas.length() - 1);
                        out.put("lastSeaAreaStatus", last == null ? "" : last.optString("status", ""));
                    }
                }
            }
            out.put("welcomeReadyFish", readyFish);

            JSONObject replica = request(AntOceanRpcCall.queryReplicaHome(), "queryReplicaHome");
            String pandoraPhase = "";
            int pandoraAsset = -1;
            if (replica != null) {
                JSONObject asset = replica.optJSONObject("userReplicaAssetVO");
                pandoraAsset = asset == null ? -1 : asset.optInt("canCollectAssetNum", -1);
                out.put("pandoraCanCollectAsset", pandoraAsset);
                JSONObject phase = replica.optJSONObject("userCurrentPhaseVO");
                pandoraPhase = phase == null ? "" : phase.optString("phaseStatus", "");
                out.put("pandoraPhaseStatus", pandoraPhase);
                JSONObject info = replica.optJSONObject("userReplicaInfoVO");
                out.put("pandoraCode", info == null ? "" : info.optString("code", ""));
            }
            out.put("welcomeReady", (readyFish < 0 ? 0 : readyFish)
                    + ("COMPLETED".equals(pandoraPhase) ? 1 : 0));

            JSONObject fish = request(AntOceanRpcCall.antfishHomepage(), "antfishHomepage");
            if (fish != null) {
                JSONObject myFish = fish.optJSONObject("myFish");
                JSONObject interact = myFish == null ? null : myFish.optJSONObject("interactVO");
                out.put("fishRemainTouchChance", interact == null ? -1 : interact.optInt("remainTouchChance", -1));
                out.put("fishTouchTotal", interact == null ? -1 : interact.optInt("touchTotal", -1));
                out.put("fishInteractStatus", interact == null ? "" : interact.optString("fishInteractStatus", ""));
                out.put("fishTouchEnergy", myFish == null ? -1 : myFish.optInt("touchEnergy", -1));
                JSONObject level = myFish == null ? null : myFish.optJSONObject("currentLevel");
                out.put("fishLevelName", level == null ? "" : level.optString("name", ""));
                JSONObject season = fish.optJSONObject("currentSeasonInfo");
                JSONObject seasonExt = season == null ? null : season.optJSONObject("extInfo");
                out.put("fishSeasonTitle", seasonExt == null ? "" : seasonExt.optString("seasonTitle", ""));
                JSONObject project = fish.optJSONObject("project");
                out.put("fishProjectName", project == null ? "" : project.optString("projectName", ""));
            }

            out.put("lastWelcomeName", OceanWelcomeStatus.loadTodayName(userId));
            out.put("at", System.currentTimeMillis());
            if (!any) {
                Log.record("海洋状态⚠️首页读取失败，本轮不写快照");
                return false;
            }
            FileUtil.write2File(out.toString(), FileUtil.getOceanStatusFile(userId));
            Log.record("海洋状态📸已更新：可迎回 " + out.optInt("welcomeReady", 0)
                    + "，可摸鱼 " + out.optInt("fishRemainTouchChance", -1)
                    + "，自家垃圾 " + out.optInt("rubbishNumber", -1)
                    + "，拼图 " + out.optInt("fixedSeaAreaNum", -1) + "/" + out.optInt("seaAreaNum", -1));
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    private static int countReadyFish(JSONArray fishVO) {
        int count = 0;
        if (fishVO == null) {
            return count;
        }
        for (int i = 0; i < fishVO.length(); i++) {
            JSONObject fish = fishVO.optJSONObject(i);
            if (fish != null && !fish.optBoolean("unlock", true)
                    && "COMPLETED".equals(fish.optString("status", ""))) {
                count++;
            }
        }
        return count;
    }

    private static JSONObject request(String raw, String name) {
        try {
            JSONObject jo = new JSONObject(raw);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.record("海洋状态⚠️" + name + "失败");
                return null;
            }
            return jo;
        } catch (Throwable t) {
            Log.err(TAG, name + " err:", t);
            return null;
        }
    }

    public static JSONObject loadToday(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            File file = FileUtil.getOceanStatusFile(userId);
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
