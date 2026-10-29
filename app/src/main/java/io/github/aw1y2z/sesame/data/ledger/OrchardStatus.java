package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchardRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 芭芭农场只读状态：每日肥料包、7 天签到、肥料余额/施肥进度、金蛋。 */
public final class OrchardStatus {

    private static final String TAG = "OrchardStatus";
    private static final Object LOCK = new Object();
    private static final int DAILY_SPREAD_LIMIT = 200;

    private OrchardStatus() {
    }

    public static boolean capture(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        try {
            JSONObject index = new JSONObject(AntOrchardRpcCall.orchardIndex());
            if (!MessageUtil.checkResultCode(TAG, index)) {
                return false;
            }
            JSONObject out = base(userId);
            readIndex(out, index);
            JSONObject extra = new JSONObject(AntOrchardRpcCall.extraInfoGet());
            if (MessageUtil.checkResultCode(TAG, extra)) {
                readFertilizerPacket(out, extra);
            }
            return write(userId, out);
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    /** 签到信息只来自任务列表：由账本抓取时顺带落盘，避免重复拉一次大列表。 */
    public static boolean captureSign(String userId, JSONObject signTaskInfo) {
        if (userId == null || userId.isEmpty() || signTaskInfo == null) {
            return false;
        }
        try {
            JSONObject out = base(userId);
            readSign(out, signTaskInfo);
            return write(userId, out);
        } catch (Throwable t) {
            Log.err(TAG, "captureSign err:", t);
            return false;
        }
    }

    public static JSONObject loadToday(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            File file = FileUtil.getOrchardStatusFile(userId);
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

    private static JSONObject base(String userId) {
        JSONObject old = loadToday(userId);
        JSONObject out = new JSONObject();
        try {
            out.put("at", System.currentTimeMillis());
            if (old != null) {
                for (String key : new String[]{
                        "fertilizer", "sign", "account", "tree", "eggs",
                        "factory", "draw", "challenge", "team"
                }) {
                    JSONObject value = old.optJSONObject(key);
                    if (value != null) {
                        out.put(key, value);
                    }
                }
                JSONArray scenes = old.optJSONArray("scenes");
                if (scenes != null) {
                    out.put("scenes", scenes);
                }
                String currentScene = old.optString("currentScene", "");
                if (!currentScene.isEmpty()) {
                    out.put("currentScene", currentScene);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void readIndex(JSONObject out, JSONObject index) throws Exception {
        JSONObject account = new JSONObject();
        JSONObject taobao = null;
        String taobaoData = index.optString("taobaoData", "");
        if (!taobaoData.isEmpty()) {
            taobao = new JSONObject(taobaoData);
        }
        JSONObject gameInfo = taobao == null ? null : taobao.optJSONObject("gameInfo");
        JSONObject accountInfo = gameInfo == null ? null : gameInfo.optJSONObject("accountInfo");
        if (accountInfo != null) {
            long happyPoint = parseLong(accountInfo.optString("happyPoint", "0"));
            int wateringCost = accountInfo.optInt("wateringCost", 0);
            int left = accountInfo.optInt("wateringLeftTimes", 0);
            account.put("happyPoint", happyPoint);
            account.put("wateringCost", wateringCost);
            account.put("wateringLeft", left);
            account.put("used", Math.max(0, DAILY_SPREAD_LIMIT - left));
            account.put("limit", DAILY_SPREAD_LIMIT);
            out.put("account", account);
        }

        JSONObject plantInfo = gameInfo == null ? null : gameInfo.optJSONObject("plantInfo");
        JSONObject seedStage = plantInfo == null ? null : plantInfo.optJSONObject("seedStage");
        if (plantInfo != null) {
            JSONObject tree = new JSONObject();
            tree.put("progress", seedStage == null ? 0 : seedStage.optInt("currentProgress", 0));
            tree.put("totalProgress", seedStage == null ? 0 : seedStage.optInt("totalProgress", 0));
            tree.put("percent", seedStage == null ? 0d : seedStage.optDouble("currentLevelProgressPercentage", 0d));
            tree.put("stageLevel", seedStage == null ? 0 : seedStage.optInt("stageLevel", 0));
            tree.put("stageMaxLevel", seedStage == null ? 0 : seedStage.optInt("stageMaxLevel", 0));
            tree.put("stageName", seedStage == null ? "" : seedStage.optString("stageName", ""));
            tree.put("stageText", seedStage == null ? "" : seedStage.optString("stageText", ""));
            tree.put("seedName", seedStage == null ? "" : seedStage.optString("seedName", ""));
            tree.put("unit", seedStage == null ? "" : seedStage.optString("unit", ""));
            tree.put("seedSubTitle", seedStage == null ? "" : seedStage.optString("seedSubTitle", ""));
            tree.put("seedValueNum", seedStage == null ? 0 : seedStage.optInt("seedValueNum", 0));
            tree.put("startTime", seedStage == null ? 0L : seedStage.optLong("startTime", 0L));
            tree.put("canExchange", plantInfo.optBoolean("canExchange", false));
            tree.put("exchangeCount", plantInfo.optInt("exchangeCount", 0));
            tree.put("seedCode", seedStage == null ? "" : seedStage.optString("seedCode", ""));
            out.put("tree", tree);
        }

        JSONObject eggs = new JSONObject();
        JSONObject eggInfo = index.optJSONObject("goldenEggInfo");
        if (eggInfo != null) {
            eggs.put("limit", eggInfo.optInt("goldenEggLimit", 0));
            eggs.put("smashed", eggInfo.optInt("smashedGoldenEggs", 0));
            eggs.put("unsmashed", eggInfo.optInt("unsmashedGoldenEggs", 0));
            eggs.put("hasAwardEgg", eggInfo.optBoolean("hasAwardEgg", false));
            eggs.put("jackpot", eggInfo.optInt("jackpotAwardCount", 0));
            out.put("eggs", eggs);
        }

        JSONArray scenes = index.optJSONArray("enableSwitchSceneList");
        if (scenes != null) {
            out.put("scenes", scenes);
        }
        String currentScene = index.optString("currentPlantScene", "");
        if (!currentScene.isEmpty()) {
            out.put("currentScene", currentScene);
        }
    }

    private static void readFertilizerPacket(JSONObject out, JSONObject extra) throws Exception {
        JSONObject data = extra.optJSONObject("data");
        JSONObject extraData = data == null ? null : data.optJSONObject("extraData");
        JSONObject packet = extraData == null ? null : extraData.optJSONObject("fertilizerPacket");
        if (packet == null) {
            return;
        }
        JSONObject fertilizer = new JSONObject();
        fertilizer.put("status", packet.optString("status", ""));
        fertilizer.put("todayNum", packet.optInt("todayFertilizerNum", 0));
        fertilizer.put("takeTime", packet.optLong("todayFertilizerTakeTime", 0L));
        fertilizer.put("tomorrowNum", packet.optInt("tomorrowFertilizerNum", 0));
        fertilizer.put("tomorrowTakeTime", packet.optLong("tomorrowFertilizerTakeTime", 0L));
        out.put("fertilizer", fertilizer);
    }

    private static void readSign(JSONObject out, JSONObject signTaskInfo) throws Exception {
        JSONObject current = signTaskInfo.optJSONObject("currentSignItem");
        JSONObject sign = new JSONObject();
        if (current != null) {
            sign.put("signed", current.optBoolean("signed", false));
            sign.put("continuousCount", current.optInt("currentContinuousCount", 0));
            sign.put("awardCount", current.optInt("awardCount", 0));
            sign.put("signKey", current.optString("signKey", ""));
        }
        sign.put("totalContinuousCount", signTaskInfo.optInt("continuousCount", 0));
        int signedCount = 0;
        JSONArray list = signTaskInfo.optJSONArray("list");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null && item.optBoolean("signed", false)) {
                    signedCount++;
                }
            }
            sign.put("totalDays", list.length());
        }
        sign.put("signedCount", signedCount);
        out.put("sign", sign);
    }

    public static boolean captureFactory(String userId, JSONObject manureFactory) {
        if (userId == null || userId.isEmpty() || manureFactory == null) {
            return false;
        }
        try {
            JSONObject factory = new JSONObject();
            factory.put("canCollect", manureFactory.optBoolean("canCollect", false));
            factory.put("canProduce", manureFactory.optBoolean("canProduce", false));
            JSONObject manure = manureFactory.optJSONObject("manure");
            JSONArray list = manure == null ? null : manure.optJSONArray("manurePotList");
            JSONArray pots = new JSONArray();
            int total = 0;
            int collectable = 0;
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject pot = list.optJSONObject(i);
                if (pot == null) {
                    continue;
                }
                int num = pot.optInt("manurePotNum", 0);
                total += num;
                if (num >= 100) {
                    collectable++;
                }
                JSONObject item = new JSONObject();
                item.put("no", pot.optString("manurePotNO", ""));
                item.put("num", num);
                item.put("limit", pot.optInt("manurePotLimit", 0));
                item.put("animalId", pot.optString("allocationAnimalId", ""));
                pots.put(item);
            }
            factory.put("total", total);
            factory.put("collectable", collectable);
            factory.put("pots", pots);
            return writeSection(userId, "factory", factory);
        } catch (Throwable t) {
            Log.err(TAG, "captureFactory err:", t);
            return false;
        }
    }

    public static boolean captureTeam(String userId, boolean inTeam) {
        try {
            JSONObject team = new JSONObject();
            team.put("inTeam", inTeam);
            return writeSection(userId, "team", team);
        } catch (Throwable t) {
            Log.err(TAG, "captureTeam err:", t);
            return false;
        }
    }

    public static boolean captureDraw(String userId, JSONObject drawAsset,
                                      String activityId, String sceneCode) {
        try {
            JSONObject draw = new JSONObject();
            draw.put("balance", drawAsset == null ? -1 : drawAsset.optInt("blance", 0));
            draw.put("activityId", activityId == null ? "" : activityId);
            draw.put("sceneCode", sceneCode == null ? "" : sceneCode);
            return writeSection(userId, "draw", draw);
        } catch (Throwable t) {
            Log.err(TAG, "captureDraw err:", t);
            return false;
        }
    }

    public static boolean captureChallenge(String userId, JSONObject challenge) {
        try {
            JSONObject out = new JSONObject();
            if (challenge == null) {
                out.put("active", false);
                return writeSection(userId, "challenge", out);
            }
            out.put("active", true);
            out.put("currentRound", challenge.optInt("currentRound", 0));
            out.put("expiredTime", challenge.optLong("expiredTime", 0L));
            out.put("totalAwardCount", challenge.optInt("totalAwardCount", 0));
            JSONArray rounds = new JSONArray();
            JSONArray list = challenge.optJSONArray("limitedTimeChallengeTasks");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject round = list.optJSONObject(i);
                if (round == null) {
                    continue;
                }
                JSONArray children = round.optJSONArray("childTaskList");
                int done = 0;
                for (int j = 0; children != null && j < children.length(); j++) {
                    JSONObject child = children.optJSONObject(j);
                    if (child != null && ("FINISHED".equals(child.optString("taskStatus"))
                            || "RECEIVED".equals(child.optString("taskStatus")))) {
                        done++;
                    }
                }
                JSONObject item = new JSONObject();
                item.put("index", i + 1);
                item.put("award", round.optInt("awardCount", 0));
                item.put("status", round.optString("taskStatus", ""));
                item.put("ongoing", round.optBoolean("ongoing", false));
                item.put("done", done);
                item.put("total", children == null ? 0 : children.length());
                rounds.put(item);
            }
            out.put("rounds", rounds);
            return writeSection(userId, "challenge", out);
        } catch (Throwable t) {
            Log.err(TAG, "captureChallenge err:", t);
            return false;
        }
    }

    private static boolean writeSection(String userId, String key, JSONObject value) {
        synchronized (LOCK) {
            JSONObject out = base(userId);
            try {
                out.put(key, value);
            } catch (Throwable ignored) {
            }
            return write(userId, out);
        }
    }

    private static boolean write(String userId, JSONObject out) {
        synchronized (LOCK) {
            return FileUtil.write2File(out.toString(), FileUtil.getOrchardStatusFile(userId));
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}
