package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antDodo.AntDodoRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 神奇物种只读总览：每日抽卡、好友帮抽（12:00 后）、道具、图鉴进度。 */
public final class SpeciesStatus {

    private static final String TAG = "SpeciesStatus";

    private SpeciesStatus() {
    }

    public static boolean capture(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            boolean dailyCollected = false;
            int dailyLeft = 0;
            String endDate = "";
            try {
                JSONObject jo = new JSONObject(AntDodoRpcCall.queryAnimalStatus());
                JSONObject data = unwrap(jo);
                if (data != null) {
                    dailyCollected = data.optBoolean("collect", false);
                }
            } catch (Throwable ignored) {
            }
            try {
                JSONObject jo = new JSONObject(AntDodoRpcCall.homePage());
                JSONObject data = unwrap(jo);
                if (data != null) {
                    JSONArray limits = data.optJSONArray("limit");
                    if (limits != null) {
                        for (int i = 0; i < limits.length(); i++) {
                            JSONObject limit = limits.optJSONObject(i);
                            if (limit != null && "DAILY_COLLECT".equals(limit.optString("actionCode"))) {
                                dailyLeft = limit.optInt("leftFreeQuota", 0);
                                break;
                            }
                        }
                    }
                    JSONObject book = data.optJSONObject("animalBook");
                    if (book != null) {
                        endDate = book.optString("endDate", "");
                    }
                }
            } catch (Throwable ignored) {
            }

            int friendLeft = 0;
            long friendStart = 0L;
            int friendAvailable = 0;
            try {
                JSONObject jo = new JSONObject(AntDodoRpcCall.queryFriend());
                JSONObject data = unwrap(jo);
                if (data != null) {
                    JSONObject extend = data.optJSONObject("extend");
                    JSONArray limits = extend == null ? null : extend.optJSONArray("limit");
                    if (limits != null) {
                        for (int i = 0; i < limits.length(); i++) {
                            JSONObject limit = limits.optJSONObject(i);
                            if (limit != null && "COLLECT_TO_FRIEND".equals(limit.optString("actionCode"))) {
                                friendLeft = limit.optInt("leftLimit", 0);
                                friendStart = limit.optLong("startTime", 0L);
                                break;
                            }
                        }
                    }
                    JSONArray friends = data.optJSONArray("friends");
                    if (friends != null) {
                        for (int i = 0; i < friends.length(); i++) {
                            JSONObject friend = friends.optJSONObject(i);
                            if (friend != null && !friend.optBoolean("dailyCollect", false)) {
                                friendAvailable++;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            int propKinds = 0;
            int propHolds = 0;
            try {
                JSONObject jo = new JSONObject(AntDodoRpcCall.propList());
                JSONObject data = unwrap(jo);
                JSONArray props = data == null ? null : data.optJSONArray("propList");
                if (props != null) {
                    propKinds = props.length();
                    for (int i = 0; i < props.length(); i++) {
                        JSONObject prop = props.optJSONObject(i);
                        if (prop != null) {
                            propHolds += Math.max(0, prop.optInt("holdsNum", 0));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            int books = 0;
            int collectedCards = 0;
            int totalCards = 0;
            int canGenerate = 0;
            try {
                int pageStart = 0;
                boolean hasMore;
                do {
                    JSONObject jo = new JSONObject(AntDodoRpcCall.queryBookList(9, pageStart));
                    JSONObject data = unwrap(jo);
                    if (data == null) {
                        break;
                    }
                    hasMore = data.optBoolean("hasMore", false);
                    pageStart += 9;
                    JSONArray list = data.optJSONArray("bookForUserList");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject book = list.optJSONObject(i);
                            if (book == null) {
                                continue;
                            }
                            books++;
                            if ("CAN_GENERATE".equals(book.optString("medalGenerationStatus"))) {
                                canGenerate++;
                            }
                            String progress = book.optString("collectProgress", "");
                            int slash = progress.indexOf('/');
                            if (slash > 0) {
                                collectedCards += parseInt(progress.substring(0, slash));
                                totalCards += parseInt(progress.substring(slash + 1));
                            }
                        }
                    }
                } while (hasMore);
            } catch (Throwable ignored) {
            }

            JSONObject out = new JSONObject();
            out.put("dailyCollected", dailyCollected);
            out.put("dailyLeft", dailyLeft);
            out.put("endDate", endDate);
            out.put("friendLeft", friendLeft);
            out.put("friendStartTime", friendStart);
            out.put("friendAvailable", friendAvailable);
            out.put("propKinds", propKinds);
            out.put("propHolds", propHolds);
            out.put("books", books);
            out.put("collectedCards", collectedCards);
            out.put("totalCards", totalCards);
            out.put("canGenerateMedals", canGenerate);
            out.put("at", System.currentTimeMillis());
            FileUtil.write2File(out.toString(), FileUtil.getSpeciesStatusFile(userId));
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    private static JSONObject unwrap(JSONObject jo) {
        if (jo == null) {
            return null;
        }
        JSONObject data = jo.optJSONObject("data");
        return data != null ? data : jo;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static JSONObject loadToday(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            File file = FileUtil.getSpeciesStatusFile(userId);
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
