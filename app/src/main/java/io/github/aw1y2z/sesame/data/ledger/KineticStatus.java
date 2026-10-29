package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antSports.AntSportsRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;
import io.github.aw1y2z.sesame.util.Status;

/** 动能只读总览：健康能量余额、每日签到、待收步数奖励气泡。 */
public final class KineticStatus {

    private static final String TAG = "KineticStatus";

    private KineticStatus() {
    }

    public static boolean capture(String userId) {
        try {
            if (RiskGuard.isPaused("sports") || RiskGuard.isGlobalPaused()
                    || RiskGuard.isVerificationRequired()) {
                return false;
            }
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            int balance = 0;
            int earnable = 0;
            try {
                JSONObject jo = new JSONObject(AntSportsRpcCall.queryCoinCenterPage());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONObject data = jo.optJSONObject("data");
                    JSONObject account = data == null ? null : data.optJSONObject("accountVO");
                    balance = account == null ? 0 : account.optInt("totalAmount", 0);
                    JSONObject tasks = data == null ? null : data.optJSONObject("taskModule");
                    earnable = tasks == null ? 0 : tasks.optInt("totalPrizeAmount", 0);
                }
            } catch (Throwable ignored) {
            }
            int bubbleCount = 0;
            int bubbleAmount = 0;
            try {
                JSONObject jo = new JSONObject(AntSportsRpcCall.queryCoinBubbleModule());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONObject data = jo.optJSONObject("data");
                    JSONArray bubbles = data == null ? null : data.optJSONArray("recBubbleList");
                    if (bubbles != null) {
                        bubbleCount = bubbles.length();
                        for (int i = 0; i < bubbles.length(); i++) {
                            JSONObject bubble = bubbles.optJSONObject(i);
                            if (bubble != null) {
                                bubbleAmount += bubble.optInt("coinAmount", 0);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            JSONObject out = new JSONObject();
            out.put("balance", balance);
            out.put("earnable", earnable);
            out.put("bubbleCount", bubbleCount);
            out.put("bubbleAmount", bubbleAmount);
            boolean signed = false;
            int signAmount = 0;
            try {
                JSONObject jo = new JSONObject(AntSportsRpcCall.querySignInCoinTask());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONObject data = jo.optJSONObject("data");
                    signed = data != null && data.optBoolean("signed", false);
                    JSONArray configs = data == null ? null : data.optJSONArray("signConfigList");
                    if (configs != null) {
                        for (int i = 0; i < configs.length(); i++) {
                            JSONObject item = configs.optJSONObject(i);
                            if (item != null && item.optBoolean("toDay", false)) {
                                signAmount = item.optInt("coinAmount", 0) + item.optInt("extraCoinAmount", 0);
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            out.put("signed", signed);
            out.put("signAmount", signAmount);

            int steps = -1;
            boolean walkSigned = false;
            int walkStepsToday = -1;
            boolean walkExchange = false;
            try {
                JSONObject stepJo = new JSONObject(AntSportsRpcCall.queryWalkStep());
                if (MessageUtil.checkResultCode(TAG, stepJo)) {
                    steps = stepJo.optInt("stepCount", -1);
                    if (steps > 0) {
                        JSONObject home = new JSONObject(AntSportsRpcCall.donateWalkHome(steps));
                        if (MessageUtil.checkResultCode(TAG, home)) {
                            JSONObject model = home.optJSONObject("walkDonateHomeModel");
                            JSONObject user = model == null ? null
                                    : model.optJSONObject("walkUserInfoModel");
                            if (user != null) {
                                walkSigned = user.optBoolean("userStepsSigned", false);
                                walkStepsToday = user.optInt("userStepsToday", -1);
                                String flag = user.optString("exchangeFlag", "");
                                walkExchange = "1".equals(flag) || "true".equalsIgnoreCase(flag);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            out.put("steps", steps);
            out.put("walkSigned", walkSigned);
            out.put("walkStepsToday", walkStepsToday);
            out.put("walkExchange", walkExchange);
            out.put("walkDonationDoneToday", Status.hasFlagToday("sport::donateWalk"));
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getKineticStatusFile(userId));
            Log.record("运动状态📸已更新：能量 " + balance + "g，步数 " + steps
                    + "，气泡 " + bubbleCount + "，签到 " + signed
                    + "，行走捐 " + (walkExchange ? "可捐" : "不可捐"));
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
            File file = FileUtil.getKineticStatusFile(userId);
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
