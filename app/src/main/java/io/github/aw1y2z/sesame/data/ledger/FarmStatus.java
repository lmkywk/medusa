package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.model.task.antFarm.AntFarmRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;
import io.github.aw1y2z.sesame.util.Status;

/**
 * 蚂蚁庄园只读状态快照：饲料、储备蛋、库存蛋、动物、肥料、待打赏、捐赠额度、家庭/睡眠。
 * <p>只解析 RPC 响应，不收取、不喂养、不捐赠、不领礼包、不收肥料；供首页和域页读取。
 */
public final class FarmStatus {

    private static final String TAG = "FarmStatus";
    private static final String FLAG_CHARITY_DONATION_DONE = "farm::donation";

    private FarmStatus() {
    }

    public static boolean capture(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        if (RiskGuard.isVerificationRequired() || RiskGuard.isGlobalPaused()) {
            Log.record("庄园状态⏸风控暂停，跳过只读抓取");
            return false;
        }
        try {
            JSONObject out = new JSONObject();
            out.put("schemaVersion", 1);
            boolean coreOk = false;

            JSONObject farm = request(AntFarmRpcCall.enterFarm("", userId), "enterFarm");
            if (farm != null) {
                JSONObject farmVO = farm.optJSONObject("farmVO");
                if (farmVO != null) {
                    coreOk = true;
                    out.put("foodStock", farmVO.optInt("foodStock", -1));
                    out.put("foodStockLimit", farmVO.optInt("foodStockLimit", -1));
                    out.put("harvestEggs", farmVO.optDouble("harvestBenevolenceScore", -1));
                    JSONObject emotion = farmVO.optJSONObject("emotionInfo");
                    out.put("emotionScore", emotion == null ? -1 : emotion.optDouble("finalScore", -1));
                    JSONObject sub = farmVO.optJSONObject("subFarmVO");
                    if (sub != null) {
                        String farmId = sub.optString("farmId", "");
                        out.put("farmId", farmId);
                        out.put("foodInTrough", sub.optInt("foodInTrough", -1));
                        JSONObject produce = sub.optJSONObject("farmProduce");
                        out.put("benevolenceEggs", produce == null ? -1 : produce.optDouble("benevolenceScore", -1));

                        int total = 0;
                        int eating = 0;
                        int hungry = 0;
                        int sleepy = 0;
                        int away = 0;
                        int hired = 0;
                        long now = System.currentTimeMillis();
                        JSONArray animals = sub.optJSONArray("animals");
                        if (animals != null) {
                            for (int i = 0; i < animals.length(); i++) {
                                JSONObject animal = animals.optJSONObject(i);
                                if (animal == null) {
                                    continue;
                                }
                                total++;
                                String currentFarmId = animal.optString("currentFarmId", "");
                                if (!farmId.isEmpty() && !farmId.equals(currentFarmId)) {
                                    away++;
                                }
                                if (animal.optLong("beHiredEndTime", 0L) > now) {
                                    hired++;
                                }
                                JSONObject status = animal.optJSONObject("animalStatusVO");
                                String feed = status == null ? "" : status.optString("animalFeedStatus", "");
                                if ("EATING".equals(feed)) {
                                    eating++;
                                } else if ("HUNGRY".equals(feed)) {
                                    hungry++;
                                } else if ("SLEEPY".equals(feed)) {
                                    sleepy++;
                                }
                            }
                        }
                        out.put("animalTotal", total);
                        out.put("animalEating", eating);
                        out.put("animalHungry", hungry);
                        out.put("animalSleepy", sleepy);
                        out.put("animalAway", away);
                        out.put("animalHired", hired);

                        int manureReady = 0;
                        JSONObject manure = sub.optJSONObject("manureVO");
                        JSONArray pots = manure == null ? null : manure.optJSONArray("manurePotList");
                        if (pots != null) {
                            for (int i = 0; i < pots.length(); i++) {
                                JSONObject pot = pots.optJSONObject(i);
                                if (pot != null && pot.optDouble("manurePotNum", 0) >= 100) {
                                    manureReady++;
                                }
                            }
                        }
                        out.put("manureReady", manureReady);
                        JSONArray rewards = sub.optJSONArray("rewardList");
                        out.put("rewardFriendCount", rewards == null ? 0 : rewards.length());
                        out.put("giftReady", sub.optJSONObject("giftRecord") != null);
                    }
                }
                JSONObject activity = farm.optJSONObject("activityData");
                JSONArray spring = activity == null ? null : activity.optJSONArray("springGifts");
                out.put("springGiftCount", spring == null ? 0 : spring.length());
                JSONArray cuisine = farm.optJSONArray("cuisineList");
                out.put("cuisineCount", cuisine == null ? 0 : cuisine.length());
                out.put("lotteryPlusReady", farm.has("lotteryPlusInfo"));
            }

            JSONObject activityInfo = request(AntFarmRpcCall.listActivityInfo(), "listActivityInfo");
            int donating = 0;
            int projectCount = 0;
            StringBuilder projects = new StringBuilder();
            if (activityInfo != null) {
                JSONArray infos = activityInfo.optJSONArray("activityInfos");
                if (infos != null) {
                    for (int i = 0; i < infos.length(); i++) {
                        JSONObject info = infos.optJSONObject(i);
                        if (info == null) {
                            continue;
                        }
                        int available = info.optInt("donationLimit", 0) - info.optInt("donationTotal", 0);
                        if (available <= 0) {
                            continue;
                        }
                        donating += available;
                        projectCount++;
                        if (projects.length() > 0) {
                            projects.append("、");
                        }
                        projects.append(info.optString("projectName", ""));
                    }
                }
                out.put("projectDonationCapacity", donating);
                out.put("donationProjectCount", projectCount);
                out.put("donationProjects", projects.toString());
            }
            out.put("charityDonationDoneToday", Status.hasFlagToday(FLAG_CHARITY_DONATION_DONE));

            JSONObject game = requestSuccess(AntFarmRpcCall.queryGameList(), "queryGameList");
            if (game != null) {
                JSONObject rights = game.optJSONObject("gameCenterDrawRights");
                out.put("parkBoxQuota", rights == null ? -1 : rights.optInt("quotaCanUse", -1));
                JSONObject coin = game.optJSONObject("gameCoinAccount");
                out.put("parkCoinBalance", coin == null ? -1 : coin.optInt("balance", -1));
            }

            JSONObject play = requestSuccess(AntFarmRpcCall.queryOptionalPlay(), "queryOptionalPlay");
            if (play != null) {
                JSONObject info = play.optJSONObject("taskTriggerPlayInfo");
                JSONArray tasks = info == null ? null : info.optJSONArray("taskList");
                int claimable = 0;
                int todo = 0;
                int total = 0;
                if (tasks != null) {
                    for (int i = 0; i < tasks.length(); i++) {
                        JSONObject task = tasks.optJSONObject(i);
                        if (task == null) {
                            continue;
                        }
                        total++;
                        String status = task.optString("taskStatus", "");
                        int award = task.optInt("awardCount", 0);
                        int received = task.optInt("alreadyReceiveAwardCount", 0);
                        int limit = task.optInt("rightsTimesLimit", 0);
                        int times = task.optInt("rightsTimes", 0);
                        if ("FINISHED".equals(status) && award > received) {
                            claimable++;
                        } else if ("TODO".equals(status) || (limit > 0 && times < limit)) {
                            todo++;
                        }
                    }
                }
                out.put("parkTaskClaimable", claimable);
                out.put("parkTaskTodo", todo);
                out.put("parkTaskTotal", total);
            }

            JSONObject kitchen = request(AntFarmRpcCall.enterKitchen(userId), "enterKitchen");
            if (kitchen != null) {
                out.put("kitchenCookTimes", kitchen.optInt("cookTimesAllowed", -1));
                out.put("kitchenDailyMaterial", kitchen.optBoolean("canCollectDailyFoodMaterial", false));
                out.put("kitchenDailyAmount", kitchen.optInt("dailyFoodMaterialAmount", -1));
                out.put("kitchenGarbage", kitchen.optInt("garbageAmount", -1));
                JSONObject orchardFood = kitchen.optJSONObject("orchardFoodMaterialStatus");
                out.put("kitchenOrchardStatus", orchardFood == null ? "" : orchardFood.optString("foodStatus", ""));
            }

            JSONObject diary = requestResult(AntFarmRpcCall.queryChickenDiaryList(), "queryChickenDiaryList");
            if (diary != null) {
                JSONObject data = diary.optJSONObject("data");
                JSONArray list = data == null ? null : data.optJSONArray("chickenDiaryBriefList");
                int unread = 0;
                String latest = "";
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        if (!item.optBoolean("read", true)) {
                            unread++;
                        }
                        String date = item.optString("dateStr", "");
                        if (date.compareTo(latest) > 0) {
                            latest = date;
                        }
                    }
                }
                out.put("diaryUnreadCount", unread);
                out.put("diaryLatestDate", latest);
                out.put("diaryTotalCount", list == null ? 0 : list.length());
            }

            JSONObject cabin = request(AntFarmRpcCall.queryLoveCabin(userId), "queryLoveCabin");
            if (cabin != null) {
                out.put("spaceType", cabin.optString("spaceType", ""));
                out.put("familyGroupId", cabin.optString("groupId", ""));
                JSONObject own = cabin.optJSONObject("ownAnimal");
                JSONObject ownStatus = own == null ? null : own.optJSONObject("animalStatusVO");
                out.put("ownFeedStatus", ownStatus == null ? "" : ownStatus.optString("animalFeedStatus", ""));
                out.put("ownInteractStatus", ownStatus == null ? "" : ownStatus.optString("animalInteractStatus", ""));
                JSONObject ownSleep = own == null ? null : own.optJSONObject("sleepInfo");
                out.put("ownSleepCountDown", ownSleep == null ? -1 : ownSleep.optLong("countDown", -1));
                JSONObject sleep = cabin.optJSONObject("sleepNotifyInfo");
                out.put("canSleep", sleep != null && sleep.optBoolean("canSleep", false));
                out.put("sleepCountDown", sleep == null ? -1 : sleep.optLong("sleepCountDown", -1));
            }

            out.put("at", System.currentTimeMillis());
            if (!coreOk) {
                Log.record("庄园状态⚠️首页读取失败，本轮不写快照");
                return false;
            }
            FileUtil.write2File(out.toString(), FileUtil.getFarmStatusFile(userId));
            Log.record("庄园状态📸已更新：饲料 " + out.optInt("foodStock", -1) + "/"
                    + out.optInt("foodStockLimit", -1) + "，储备蛋 " + out.optDouble("harvestEggs", 0)
                    + "，库存蛋 " + out.optDouble("benevolenceEggs", 0)
                    + "，可收肥料 " + out.optInt("manureReady", 0)
                    + "，待打赏 " + out.optInt("rewardFriendCount", 0)
                    + "，离家 " + out.optInt("animalAway", 0)
                    + "，宝箱 " + out.optInt("parkBoxQuota", -1)
                    + "，可做菜 " + out.optInt("kitchenCookTimes", -1)
                    + "，日记未读 " + out.optInt("diaryUnreadCount", -1));
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    private static JSONObject request(String raw, String name) {
        try {
            JSONObject jo = new JSONObject(raw);
            if (!MessageUtil.checkMemo(TAG, jo)) {
                Log.record("庄园状态⚠️" + name + "失败");
                return null;
            }
            return jo;
        } catch (Throwable t) {
            Log.err(TAG, name + " err:", t);
            return null;
        }
    }

    private static JSONObject requestSuccess(String raw, String name) {
        try {
            JSONObject jo = new JSONObject(raw);
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                Log.record("庄园状态⚠️" + name + "失败");
                return null;
            }
            return jo;
        } catch (Throwable t) {
            Log.err(TAG, name + " err:", t);
            return null;
        }
    }

    private static JSONObject requestResult(String raw, String name) {
        try {
            JSONObject jo = new JSONObject(raw);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.record("庄园状态⚠️" + name + "失败");
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
            File file = FileUtil.getFarmStatusFile(userId);
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
