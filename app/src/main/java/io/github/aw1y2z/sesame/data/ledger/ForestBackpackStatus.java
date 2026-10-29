package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 森林背包只读快照：道具名、数量、到期时间。 */
public final class ForestBackpackStatus {

    private static final String TAG = "ForestBackpackStatus";

    private ForestBackpackStatus() {
    }

    public static boolean capture(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            JSONObject jo = new JSONObject(AntForestRpcCall.queryPropList(false));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray props = jo.optJSONArray("forestPropVOList");
            JSONArray outList = new JSONArray();
            long now = System.currentTimeMillis();
            if (props != null) {
                for (int i = 0; i < props.length(); i++) {
                    JSONObject prop = props.optJSONObject(i);
                    if (prop == null) {
                        continue;
                    }
                    JSONObject config = prop.optJSONObject("propConfigVO");
                    String name = config == null ? "" : config.optString("propName", "");
                    if (name.isEmpty()) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("name", name);
                    item.put("group", prop.optString("propGroup", ""));
                    item.put("count", prop.optInt("holdsNum", 0));
                    item.put("expireAt", prop.optLong("recentExpireTime", 0L));
                    outList.put(item);
                }
            }
            JSONObject out = new JSONObject();
            out.put("items", outList);
            out.put("at", now);
            FileUtil.write2File(out.toString(), FileUtil.getForestBackpackFile(userId));
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
            File file = FileUtil.getForestBackpackFile(userId);
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
