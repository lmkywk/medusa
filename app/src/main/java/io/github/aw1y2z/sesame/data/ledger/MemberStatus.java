package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.data.plan.PlanTargets;
import io.github.aw1y2z.sesame.model.task.antMember.AntMemberRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;

/** 会员只读总览：待领积分凭证、任务数、签到、权益数。 */
public final class MemberStatus {

    private static final String TAG = "MemberStatus";

    private MemberStatus() {
    }

    public static boolean capture(String userId) {
        try {
            if (RiskGuard.isPaused(PlanTargets.MEMBER) || RiskGuard.isGlobalPaused()
                    || RiskGuard.isVerificationRequired()) {
                return false;
            }
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            int pointClaimable = 0;
            int certCount = 0;
            try {
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryPointCert(1, 50));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray certList = jo.optJSONArray("certList");
                    if (certList != null) {
                        certCount = certList.length();
                        for (int i = 0; i < certList.length(); i++) {
                            pointClaimable += certList.optJSONObject(i) == null ? 0
                                    : certList.optJSONObject(i).optInt("pointAmount", 0);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            int taskAvailable = 0;
            int taskHistory = 0;
            try {
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryAllStatusTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray available = jo.optJSONArray("availableTaskList");
                    JSONArray history = jo.optJSONArray("taskHistoryList");
                    taskAvailable = available == null ? 0 : available.length();
                    taskHistory = history == null ? 0 : history.length();
                }
            } catch (Throwable ignored) {
            }
            boolean autoSignIn = false;
            int signinSumDay = 0;
            int signinPoint = 0;
            try {
                JSONObject jo = new JSONObject(AntMemberRpcCall.queryMemberSigninCalendar());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    autoSignIn = jo.optBoolean("autoSignInSuccess", false);
                    signinSumDay = jo.optInt("signinSumDay", 0);
                    signinPoint = jo.optInt("signinPoint", 0);
                }
            } catch (Throwable ignored) {
            }
            int benefitCount = 0;
            try {
                File file = FileUtil.getMemberBenefitIdMapFile(userId);
                if (file.exists()) {
                    String json = FileUtil.readFromFile(file);
                    if (json != null && !json.trim().isEmpty()) {
                        benefitCount = new JSONObject(json).length();
                    }
                }
            } catch (Throwable ignored) {
            }
            JSONObject out = new JSONObject();
            out.put("pointClaimable", pointClaimable);
            out.put("certCount", certCount);
            out.put("taskAvailable", taskAvailable);
            out.put("taskHistory", taskHistory);
            out.put("autoSignInSuccess", autoSignIn);
            out.put("signinSumDay", signinSumDay);
            out.put("signinPoint", signinPoint);
            out.put("benefitCount", benefitCount);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getMemberStatusFile(userId));
            Log.record("会员状态📸已更新：待领积分 " + pointClaimable + "，凭证 " + certCount
                    + "，任务 " + taskAvailable + "，签到 " + autoSignIn + "，权益 " + benefitCount);
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
            File file = FileUtil.getMemberStatusFile(userId);
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
