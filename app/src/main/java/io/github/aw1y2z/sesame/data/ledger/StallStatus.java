package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.data.plan.PlanTargets;
import io.github.aw1y2z.sesame.model.task.antStall.AntStallRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;

/** 蚂蚁新村只读总览：注册/村庄、新村币、店铺数、任务数、肥料。 */
public final class StallStatus {

    private static final String TAG = "StallStatus";

    private StallStatus() {
    }

    public static boolean capture(String userId) {
        try {
            if (RiskGuard.isPaused(PlanTargets.STALL) || RiskGuard.isGlobalPaused()
                    || RiskGuard.isVerificationRequired()) {
                return false;
            }
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            boolean registered = false;
            boolean quit = false;
            String villageName = "";
            String villageType = "";
            int coin = 0;
            int shopCount = 0;
            try {
                JSONObject jo = new JSONObject(AntStallRpcCall.selfHome(""));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    registered = jo.optBoolean("hasRegister", false);
                    quit = jo.optBoolean("hasQuit", false);
                    JSONObject village = jo.optJSONObject("currentVillage");
                    if (village != null) {
                        villageName = village.optString("villageName",
                                village.optString("name", ""));
                        villageType = village.optString("villageType", "");
                    }
                    JSONObject receivable = jo.optJSONObject("astReceivableCoinVO");
                    if (receivable != null) {
                        coin = receivable.optInt("amount",
                                receivable.optInt("coinAmount",
                                        receivable.optInt("receivableCoin", 0)));
                    }
                    JSONObject userInfo = jo.optJSONObject("userInfo");
                    if (userInfo != null && coin <= 0) {
                        coin = userInfo.optInt("coin",
                                userInfo.optInt("totalCoin", userInfo.optInt("coinAmount", 0)));
                    }
                    JSONObject seats = jo.optJSONObject("seatsMap");
                    if (seats != null) {
                        shopCount = seats.length();
                    } else {
                        JSONArray seatList = jo.optJSONArray("seatsMap");
                        shopCount = seatList == null ? 0 : seatList.length();
                    }
                }
            } catch (Throwable ignored) {
            }
            int taskCount = 0;
            try {
                JSONObject jo = new JSONObject(AntStallRpcCall.taskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray tasks = jo.optJSONArray("taskModels");
                    taskCount = tasks == null ? 0 : tasks.length();
                }
            } catch (Throwable ignored) {
            }
            int manure = 0;
            try {
                JSONObject jo = new JSONObject(AntStallRpcCall.queryManureInfo());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    manure = jo.optInt("manureNum",
                            jo.optInt("manureAmount", jo.optInt("manure", 0)));
                }
            } catch (Throwable ignored) {
            }
            JSONObject out = new JSONObject();
            out.put("registered", registered);
            out.put("quit", quit);
            out.put("villageName", villageName);
            out.put("villageType", villageType);
            out.put("coin", coin);
            out.put("shopCount", shopCount);
            out.put("taskCount", taskCount);
            out.put("manure", manure);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getStallStatusFile(userId));
            Log.record("新村状态📸已更新：村庄 " + villageName + "，新村币 " + coin
                    + "，店铺 " + shopCount + "，任务 " + taskCount + "，肥料 " + manure);
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
            File file = FileUtil.getStallStatusFile(userId);
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
