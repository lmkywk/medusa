package io.github.aw1y2z.sesame.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 加好友能力探测：只枚举社交 SDK 里已有的类和方法签名，不调用任何接口。
 * 结果写到共享目录，供 UI 侧判断「一键加好友」是否有干净的接口路径。
 */
public final class FriendCapabilityProbe {

    private static final String TAG = "FriendCapabilityProbe";

    /** 已知在用或可能承载好友申请的类；不存在的直接跳过。 */
    private static final String[] CANDIDATES = new String[]{
            "com.alipay.mobile.socialcommonsdk.bizdata.contact.data.AliAccountDaoOp",
            "com.alipay.mobile.socialcommonsdk.bizdata.UserIndependentCache",
            "com.alipay.mobile.socialcommonsdk.bizdata.contact.data.AliAccountData",
            "com.alipay.mobile.socialcommonsdk.bizdata.contact.data.FriendRequest",
            "com.alipay.mobile.socialcommonsdk.bizdata.contact.data.FriendApplyInfo",
            "com.alipay.mobile.socialcommonsdk.bizdata.contact.ContactDataManager",
            "com.alipay.mobile.socialcommonsdk.bizdata.contact.data.ContactDataManager"
    };

    private FriendCapabilityProbe() {
    }

    public static void probe(ClassLoader loader) {
        if (loader == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("at", System.currentTimeMillis());
            JSONArray classes = new JSONArray();
            for (String name : CANDIDATES) {
                JSONObject item = new JSONObject();
                item.put("class", name);
                try {
                    Class<?> clazz = Class.forName(name, false, loader);
                    item.put("found", true);
                    item.put("methods", methodNames(clazz));
                } catch (Throwable t) {
                    item.put("found", false);
                    item.put("error", t.getClass().getSimpleName());
                }
                classes.put(item);
            }
            root.put("classes", classes);
            root.put("note", "只枚举方法签名，不发起任何好友请求");
            FileUtil.write2File(root.toString(), FileUtil.getFriendCapabilityProbeFile());
            Log.record("加好友能力探测已写入");
        } catch (Throwable t) {
            Log.err(TAG, "probe err:", t);
        }
    }

    private static JSONArray methodNames(Class<?> clazz) {
        JSONArray array = new JSONArray();
        try {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isSynthetic()) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(Modifier.isStatic(method.getModifiers()) ? "static " : "");
                sb.append(method.getReturnType().getSimpleName()).append(' ');
                sb.append(method.getName()).append('(');
                Class<?>[] params = method.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(params[i].getSimpleName());
                }
                sb.append(')');
                array.put(sb.toString());
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return array;
    }
}
