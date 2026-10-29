package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.StringUtil;

/**
 * PK 赛名单只读缓存：森林能量挑战榜 / 庄园爱心鸡捐蛋榜。
 * 只保存成员 id、显示名、名次、分数；每次抓取覆盖该域，另一个域的数据保留。
 */
public final class PkRoster {

    private static final String TAG = "PkRoster";
    public static final String DOMAIN_FOREST = "forest";
    public static final String DOMAIN_FARM = "farm";

    private PkRoster() {
    }

    public static synchronized void record(String userId, String domain, JSONArray members, boolean joined) {
        if (StringUtil.isEmpty(userId) || StringUtil.isEmpty(domain) || members == null) {
            return;
        }
        try {
            JSONObject root = loadAll(userId);
            JSONObject node = new JSONObject();
            node.put("at", System.currentTimeMillis());
            node.put("joined", joined);
            node.put("members", members);
            root.put(domain, node);
            FileUtil.write2File(root.toString(), FileUtil.getPkRosterFile(userId));
        } catch (Throwable t) {
            Log.err(TAG, "record err:", t);
        }
    }

    public static JSONObject loadAll(String userId) {
        try {
            if (StringUtil.isEmpty(userId)) {
                return new JSONObject();
            }
            String text = FileUtil.readFromFile(FileUtil.getPkRosterFile(userId));
            if (StringUtil.isEmpty(text)) {
                return new JSONObject();
            }
            return new JSONObject(text);
        } catch (Throwable t) {
            return new JSONObject();
        }
    }
}
