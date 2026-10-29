package io.github.aw1y2z.sesame.util;

import org.json.JSONObject;

import java.io.File;

/**
 * 首页展示用的支付宝资料（昵称/头像）。
 * <p>注入进程从「我的账号模型」与好友库补全后写入共享目录，模块 App 只读；
 * 这样首页不必依赖模块进程里不存在的宿主类。
 */
public final class ProfileStore {

    private static final String TAG = ProfileStore.class.getSimpleName();
    private static final String FILE_NAME = "profile.json";

    private ProfileStore() {
    }

    public static class Profile {
        public String userId;
        public String name;
        public String avatarUrl;
    }

    public static File getFile() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, FILE_NAME);
    }

    public static Profile load() {
        try {
            File file = getFile();
            if (!file.exists() || !file.canRead()) {
                return null;
            }
            String body = FileUtil.readFromFile(file);
            if (body == null || body.trim().isEmpty()) {
                return null;
            }
            JSONObject jo = new JSONObject(body);
            Profile profile = new Profile();
            profile.userId = jo.optString("userId", "");
            profile.name = jo.optString("name", "");
            profile.avatarUrl = jo.optString("avatarUrl", "");
            return profile;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return null;
    }

    public static void save(String userId, String name, String avatarUrl) {
        if (StringUtil.isEmpty(userId)) {
            return;
        }
        try {
            JSONObject jo = new JSONObject();
            jo.put("userId", userId);
            jo.put("name", name == null ? "" : name);
            jo.put("avatarUrl", avatarUrl == null ? "" : avatarUrl);
            FileUtil.write2File(jo.toString(), getFile());
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
}
