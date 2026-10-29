package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.protectEcology.CooperateRpcCall;
import io.github.aw1y2z.sesame.model.task.protectEcology.ProtectOceanRpcCall;
import io.github.aw1y2z.sesame.model.task.protectEcology.ProtectTreeRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 生态保护只读总览：可保护项目、证书数、碳中和活动、海滩、合种。 */
public final class ProtectStatus {

    private static final String TAG = "ProtectStatus";

    private ProtectStatus() {
    }

    public static boolean capture(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            JSONObject out = new JSONObject();
            boolean any = false;

            try {
                JSONObject jo = new JSONObject(
                        ProtectTreeRpcCall.queryTreeItemsForExchange("AVAILABLE", "project"));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray items = jo.optJSONArray("treeItems");
                    if (items != null) {
                        int trees = 0;
                        int reserves = 0;
                        int animals = 0;
                        int certs = 0;
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) {
                                continue;
                            }
                            switch (item.optString("projectType")) {
                                case "TREE":
                                    trees++;
                                    break;
                                case "RESERVE":
                                    reserves++;
                                    break;
                                case "ANIMAL":
                                    animals++;
                                    break;
                                default:
                                    break;
                            }
                            certs += Math.max(
                                    item.optInt("certCountForAlias", 0),
                                    item.optInt("certCount", 0));
                        }
                        out.put("projects", trees + reserves + animals);
                        out.put("trees", trees);
                        out.put("reserves", reserves);
                        out.put("animals", animals);
                        out.put("certs", certs);
                        any = true;
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                JSONObject jo = new JSONObject(
                        ProtectTreeRpcCall.queryTreeItemsForExchange("AVAILABLE", "special"));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray items = jo.optJSONArray("treeItems");
                    if (items != null) {
                        int carbon = 0;
                        int marathon = 0;
                        int doctor = 0;
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            JSONObject extend = item == null ? null : item.optJSONObject("extendInfo");
                            if (extend == null) {
                                continue;
                            }
                            carbon++;
                            if ("marathon".equals(extend.optString("activityType"))) {
                                marathon++;
                            } else if (extend.optString("activityName").contains("古树医生")) {
                                doctor++;
                            }
                        }
                        out.put("carbon", carbon);
                        out.put("marathon", marathon);
                        out.put("doctor", doctor);
                        any = true;
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                JSONObject jo = new JSONObject(ProtectOceanRpcCall.queryCultivationList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray items = jo.optJSONArray("cultivationItemVOList");
                    if (items != null) {
                        int beaches = 0;
                        int beachCerts = 0;
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null || !"AVAILABLE".equals(item.optString("applyAction"))) {
                                continue;
                            }
                            String templateType = item.optString("templateType");
                            if (!"BEACH".equals(item.optString("templateSubType"))
                                    && !"COOPERATE_PLANT".equals(templateType)
                                    && !"PROTECT".equals(templateType)) {
                                continue;
                            }
                            beaches++;
                            beachCerts += Math.max(0, item.optInt("certNum", 0));
                        }
                        out.put("beaches", beaches);
                        out.put("beachCerts", beachCerts);
                        any = true;
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                JSONObject jo = new JSONObject(CooperateRpcCall.queryUserCooperatePlantList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray items = jo.optJSONArray("cooperatePlants");
                    if (items != null) {
                        out.put("cooperate", items.length());
                        any = true;
                    }
                }
            } catch (Throwable ignored) {
            }

            if (!any) {
                return false;
            }
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getProtectStatusFile(userId));
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
            File file = FileUtil.getProtectStatusFile(userId);
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
