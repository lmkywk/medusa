package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** IP 联名馆：发现当前保护罩商品、缓存今日兑换数量。 */
public final class ForestIpStatus {

    private static final String TAG = "ForestIpStatus";
    private static final String ACTIVITY_ID = "20251024";

    private ForestIpStatus() {
    }

    public static JSONObject capture(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            JSONObject out = loadToday(userId);
            if (out == null) {
                out = new JSONObject();
                out.put("todayExchanged", 0);
            }
            int energy = 0;
            try {
                JSONObject home = new JSONObject(AntForestRpcCall.queryHomePage());
                JSONObject biz = home.optJSONObject("resData");
                if (biz != null) {
                    home = biz;
                }
                if (MessageUtil.checkResultCode(TAG, home)) {
                    JSONObject base = home.optJSONObject("userBaseInfo");
                    if (base != null) {
                        energy = base.optInt("currentEnergy", 0);
                    }
                }
            } catch (Throwable ignored) {
            }

            JSONObject shield = findShield();
            if (shield != null) {
                out.put("itemName", shield.optString("skuName", "保护罩"));
                out.put("skuId", shield.optString("skuId"));
                out.put("spuId", shield.optString("spuId"));
                out.put("price", shield.optInt("price", 500));
            }
            out.put("currentEnergy", energy);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getForestIpFile(userId));
            return out;
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return null;
        }
    }

    private static JSONObject findShield() {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.topNEnergyMallItems(ACTIVITY_ID));
            JSONObject biz = jo.optJSONObject("resData");
            if (biz != null) {
                jo = biz;
            }
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            JSONArray items = jo.optJSONArray("itemInfoVOList");
            if (items == null) {
                return null;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String spuName = item.optString("spuName", "");
                JSONArray skus = item.optJSONArray("skuModelList");
                if (skus == null) {
                    continue;
                }
                for (int j = 0; j < skus.length(); j++) {
                    JSONObject sku = skus.optJSONObject(j);
                    if (sku == null) {
                        continue;
                    }
                    String skuName = sku.optString("skuName", "");
                    if (!spuName.contains("保护罩") && !skuName.contains("保护罩")) {
                        continue;
                    }
                    JSONObject priceObj = sku.optJSONObject("price");
                    JSONObject out = new JSONObject();
                    out.put("skuName", skuName.isEmpty() ? spuName : skuName);
                    out.put("skuId", sku.optString("skuId", ""));
                    out.put("spuId", sku.optString("spuId", item.optString("spuId", "")));
                    out.put("price", priceObj == null ? 500 : (int) priceObj.optDouble("amount", 500));
                    return out;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "findShield err:", t);
        }
        return null;
    }

    public static void recordExchange(String userId, String orderNo) {
        try {
            if (userId == null || userId.isEmpty()) {
                return;
            }
            JSONObject out = loadToday(userId);
            if (out == null) {
                out = new JSONObject();
            }
            out.put("todayExchanged", out.optInt("todayExchanged", 0) + 1);
            out.put("lastOrderNo", orderNo == null ? "" : orderNo);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getForestIpFile(userId));
        } catch (Throwable t) {
            Log.err(TAG, "recordExchange err:", t);
        }
    }

    public static JSONObject loadToday(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            File file = FileUtil.getForestIpFile(userId);
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
